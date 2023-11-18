package pl.eukon05.pk4j.core;

import pl.eukon05.pk4j.core.impl.scraper.ScraperPK4J;
import pl.eukon05.pk4j.model.Announcement;
import pl.eukon05.pk4j.model.UserDetails;

import java.io.IOException;
import java.util.List;

public interface PK4J {
    List<Announcement> getAnnouncements(EHMSUser user) throws IOException;

    UserDetails getUserDetails(EHMSUser user) throws IOException;

    static PK4J getInstance() {
        return new ScraperPK4J();
    }

    /* Not yet implemented
    static PK4J getInstance(boolean useApi){
        return useApi ? null : new ScraperPK4J();
    }
     */
}
