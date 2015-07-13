package com.googlecode.npackdweb;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.googlecode.npackdweb.db.License;
import com.googlecode.npackdweb.db.Package;
import com.googlecode.npackdweb.db.PackageVersion;
import com.googlecode.npackdweb.wlib.Action;
import com.googlecode.npackdweb.wlib.ActionSecurityType;
import com.googlecode.npackdweb.wlib.Page;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Upload a repository.
 */
public class RepUploadAction extends Action {

    private static final class Found {

        List<License> lics;
        public List<Package> ps;
        public List<PackageVersion> pvs;
    }

    private static final class Stats {

        public int pOverwritten, pAppended, pExisting;
        public int pvOverwritten, pvAppended, pvExisting;
        public int licOverwritten, licAppended, licExisting;
    }

    /**
     * -
     */
    public RepUploadAction() {
        super("^/rep/upload$", ActionSecurityType.LOGGED_IN);
    }

    @Override
    public Page perform(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        List<String> messages = new ArrayList<String>();

        Found f = null;
        String tag = "unknown";
        boolean overwrite = false;
        if (ServletFileUpload.isMultipartContent(req)) {
            ServletFileUpload upload = new ServletFileUpload();
            FileItemIterator iterator;
            try {
                iterator = upload.getItemIterator(req);
                while (iterator.hasNext()) {
                    FileItemStream item = iterator.next();
                    InputStream stream = item.openStream();

                    try {
                        if (item.isFormField()) {
                            if (item.getFieldName().equals("tag")) {
                                BufferedReader r =
                                        new BufferedReader(
                                                new InputStreamReader(stream));
                                tag = r.readLine();
                            } else if (item.getFieldName().equals("overwrite")) {
                                overwrite = true;
                            }
                        } else {
                            f = process(stream);
                        }
                    } finally {
                        stream.close();
                    }
                }
            } catch (Exception e) {
                messages.add("Error reading the data: " + e.getMessage());
            }
        } else {
            tag = req.getParameter("tag");
            String rep = req.getParameter("repository");
            overwrite = req.getParameter("overwrite") != null;
            try {
                f = process(new ByteArrayInputStream(rep.getBytes("UTF-8")));
            } catch (IOException e) {
                messages.add("Error reading the data: " + e.getMessage());
            }
        }

        if (f != null) {
            boolean isAdmin = NWUtils.isAdminLoggedIn();

            for (PackageVersion pv : f.pvs) {
                pv.tags.add(tag);
            }

            Objectify ofy = DefaultServlet.getObjectify();
            List<Key<?>> keys = new ArrayList<Key<?>>();
            for (License lic : f.lics) {
                keys.add(lic.createKey());
            }
            for (PackageVersion pv : f.pvs) {
                keys.add(pv.createKey());
            }
            for (Package p : f.ps) {
                keys.add(p.createKey());
            }

            Map<Key<Object>, Object> existing = ofy.get(keys);

            Stats stats = new Stats();
            Iterator<PackageVersion> it = f.pvs.iterator();
            while (it.hasNext()) {
                PackageVersion pv = it.next();
                PackageVersion found =
                        (PackageVersion) existing.get(pv.createKey());
                if (found != null) {
                    stats.pvExisting++;
                    if (!overwrite) {
                        it.remove();
                    }
                }
            }

            Iterator<License> itLic = f.lics.iterator();
            while (itLic.hasNext()) {
                License pv = itLic.next();
                License found = (License) existing.get(pv.createKey());
                if (found != null) {
                    stats.licExisting++;
                    if (!overwrite) {
                        itLic.remove();
                    }
                }
            }

            Iterator<Package> itP = f.ps.iterator();
            while (itP.hasNext()) {
                Package p = itP.next();
                Package found = (Package) existing.get(p.createKey());
                if (found != null) {
                    stats.pExisting++;
                    if (!overwrite) {
                        itP.remove();
                    }
                }
            }

            for (PackageVersion pv : f.pvs) {
                Package p =
                        ofy.find(new Key<Package>(Package.class, pv.package_));
                if (p != null && !p.isCurrentUserPermittedToModify()) {
                    messages.add(
                            "You do not have permission to modify this package: " +
                            pv.package_);
                } else {
                    NWUtils.savePackageVersion(ofy, pv, true, true);
                }
            }

            for (Package p : f.ps) {
                Package p_ = ofy.find(new Key<Package>(Package.class, p.name));
                if (p_ != null && !p_.isCurrentUserPermittedToModify()) {
                    messages.add(
                            "You do not have permission to modify this package: " +
                            p.name);
                } else {
                    NWUtils.savePackage(ofy, p, true);
                }
            }

            if (f.lics.size() > 0) {
                if (isAdmin) {
                    ofy.put(f.lics);
                } else {
                    messages.add("Only an administrator can change licenses");
                }
            }

            if (overwrite) {
                stats.pOverwritten = stats.pExisting;
                stats.pvOverwritten = stats.pvExisting;
                stats.licOverwritten = stats.licExisting;
                stats.pAppended = f.ps.size() - stats.pOverwritten;
                stats.pvAppended = f.pvs.size() - stats.pvOverwritten;
                stats.licAppended = f.lics.size() - stats.licOverwritten;
            } else {
                stats.pAppended = f.ps.size();
                stats.pvAppended = f.pvs.size();
                stats.licAppended = f.lics.size();
            }
            messages.add(stats.pOverwritten + " packages overwritten, " +
                    stats.pvOverwritten + " package versions overwritten, " +
                    stats.licOverwritten + " licenses overwritten, " +
                    stats.pAppended + " packages appended, " +
                    stats.pvAppended + " package versions appended, " +
                    stats.licAppended + " licenses appended");
        } else {
            messages.add("No data found");
        }

        return new MessagePage(messages);
    }

    private Found process(InputStream stream) throws IOException {
        Found f = null;
        try {
            DocumentBuilder db =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder();
            Document d = db.parse(stream);
            f = process(d);
        } catch (SAXParseException e) {
            throw new IOException("XML parsing error at " + e.getLineNumber() +
                    ":" + e.getColumnNumber() + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return f;
    }

    private Found process(Document d) {
        Found f = new Found();
        Element root = d.getDocumentElement();
        f.lics = processLicenses(root.getChildNodes());
        f.ps = processPackages(root.getChildNodes());
        f.pvs = processPackageVersions(root.getChildNodes());

        return f;
    }

    private List<License> processLicenses(NodeList children) {
        List<License> v = new ArrayList<License>();
        for (int i = 0; i < children.getLength(); i++) {
            Node ch = children.item(i);
            if (ch.getNodeType() == Element.ELEMENT_NODE &&
                    ch.getNodeName().equals("license")) {
                Element license = (Element) ch;
                License lic = new License();
                lic.name = license.getAttribute("name");
                lic.title = NWUtils.getSubTagContent(license, "title", "");
                lic.url = NWUtils.getSubTagContent(license, "url", "");
                v.add(lic);
            }
        }
        return v;
    }

    private List<Package> processPackages(NodeList children) {
        List<Package> v = new ArrayList<Package>();
        for (int i = 0; i < children.getLength(); i++) {
            Node ch = children.item(i);
            if (ch.getNodeType() == Element.ELEMENT_NODE &&
                    ch.getNodeName().equals("package")) {
                Element e = (Element) ch;
                Package p = Package.parse(e);
                v.add(p);
            }
        }
        return v;
    }

    private List<PackageVersion> processPackageVersions(NodeList children) {
        List<PackageVersion> v = new ArrayList<PackageVersion>();
        for (int i = 0; i < children.getLength(); i++) {
            Node ch = children.item(i);
            if (ch.getNodeType() == Element.ELEMENT_NODE &&
                    ch.getNodeName().equals("version")) {
                Element e = (Element) ch;
                PackageVersion pv = createPackageVersion(e);
                v.add(pv);
            }
        }
        return v;
    }

    private PackageVersion createPackageVersion(Element e) {
        PackageVersion p =
                new PackageVersion(e.getAttribute("package"),
                        e.getAttribute("name"));
        p.name = p.package_ + "@" + p.version;
        p.oneFile = e.getAttribute("type").equals("one-file");
        p.url = NWUtils.getSubTagContent(e, "url", "");
        p.sha1 = NWUtils.getSubTagContent(e, "sha1", "");
        p.detectMSI = NWUtils.getSubTagContent(e, "detect-msi", "");

        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node ch = children.item(i);
            if (ch.getNodeType() == Element.ELEMENT_NODE) {
                Element che = (Element) ch;
                if (che.getNodeName().equals("important-file")) {
                    p.importantFilePaths.add(che.getAttribute("path"));
                    p.importantFileTitles.add(che.getAttribute("title"));
                } else if (che.getNodeName().equals("file")) {
                    p.addFile(che.getAttribute("path"),
                            NWUtils.getTagContent_(che));
                } else if (che.getNodeName().equals("dependency")) {
                    p.dependencyPackages.add(che.getAttribute("package"));
                    p.dependencyVersionRanges.add(che.getAttribute("versions"));
                    p.dependencyEnvVars.add(NWUtils.getSubTagContent(che,
                            "variable", ""));
                }
            }
        }
        return p;
    }
}
