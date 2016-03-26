package com.googlecode.npackdweb;

import com.googlecode.npackdweb.db.Repository;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;

/**
 * Repository details.
 */
public class RepDetailPage extends MyPage {

    private Repository r;

    /**
     * @param r a repository
     */
    public RepDetailPage(Repository r) {
        this.r = r;
    }

    @Override
    public String createContent(HttpServletRequest request) throws IOException {
        return NWUtils.tmpl("rep/Repository.html", NWUtils.newMap("title",
                r.name, "id", r.name));
    }

    @Override
    public String getTitle() {
        return "Repository";
    }
}
