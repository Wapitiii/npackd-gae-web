package com.googlecode.npackdweb.pv;

import com.googlecode.npackdweb.DefaultServlet;
import com.googlecode.npackdweb.MessagePage;
import com.googlecode.npackdweb.NWUtils;
import com.googlecode.npackdweb.NWUtils.Info;
import com.googlecode.npackdweb.db.Package;
import com.googlecode.npackdweb.db.PackageVersion;
import com.googlecode.npackdweb.wlib.Action;
import com.googlecode.npackdweb.wlib.ActionSecurityType;
import com.googlecode.npackdweb.wlib.Page;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Compute SHA1 for a package version.
 */
public class PackageVersionComputeSHA1Action extends Action {

    /**
     * -
     */
    public PackageVersionComputeSHA1Action() {
        super("^/package-version/compute-sha1$", ActionSecurityType.LOGGED_IN);
    }

    @Override
    public Page perform(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String package_ = req.getParameter("package");
        String version = req.getParameter("version");
        Objectify ofy = DefaultServlet.getObjectify();
        Package pa = ofy.get(new Key<Package>(Package.class, package_));
        Page page;
        if (!pa.isCurrentUserPermittedToModify()) {
            page =
                    new MessagePage(
                            "You do not have permission to modify this package");
        } else {
            PackageVersion p =
                    ofy.get(new Key<PackageVersion>(PackageVersion.class,
                                    package_ + "@" + version));
            PackageVersion oldp = p.copy();
            Info info = p.check(false, "SHA-1");
            if (info != null) {
                p.sha1 = NWUtils.byteArrayToHexString(info.sha1);
            }
            NWUtils.savePackageVersion(ofy, oldp, p, true, true);
            if (p.downloadCheckError != null) {
                page =
                        new MessagePage("Cannot download the file: " +
                                p.downloadCheckError);
            } else {
                resp.sendRedirect("/p/" + p.package_ + "/" + p.version);
                page = null;
            }
        }
        return page;
    }
}
