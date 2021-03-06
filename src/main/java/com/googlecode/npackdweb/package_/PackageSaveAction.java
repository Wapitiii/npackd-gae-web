package com.googlecode.npackdweb.package_;

import com.googlecode.npackdweb.FormMode;
import com.googlecode.npackdweb.MessagePage;
import com.googlecode.npackdweb.NWUtils;
import com.googlecode.npackdweb.db.Package;
import com.googlecode.npackdweb.wlib.Action;
import com.googlecode.npackdweb.wlib.ActionSecurityType;
import com.googlecode.npackdweb.wlib.Page;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Save or create a package.
 */
public class PackageSaveAction extends Action {

    /**
     * -
     */
    public PackageSaveAction() {
        super("^/package/save$", ActionSecurityType.LOGGED_IN);
    }

    @Override
    public Page perform(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String name = req.getParameter("name");
        PackageDetailPage pdp = new PackageDetailPage(FormMode.EDIT);
        pdp.fill(req);
        String msg = pdp.validate();
        if (msg == null) {
            Package p = NWUtils.dsCache.getPackage(name, false);
            Package old = p == null ? null : p.copy();
            if (p == null) {
                p = new Package(name);
            } else if (!p.isCurrentUserPermittedToModify()) {
                return new MessagePage(
                        "You do not have permission to modify this package");
            }
            pdp.fillObject(p);
            NWUtils.dsCache.savePackage(old, p, true);
            pdp = null;
            resp.sendRedirect("/p/" + p.name);
        } else {
            pdp.error = msg;
        }
        return pdp;
    }
}
