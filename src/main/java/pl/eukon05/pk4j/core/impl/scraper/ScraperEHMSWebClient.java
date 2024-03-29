package pl.eukon05.pk4j.core.impl.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.eukon05.pk4j.core.EHMSUser;
import pl.eukon05.pk4j.exception.EHMSException;
import pl.eukon05.pk4j.exception.RateLimitExceededException;

import java.io.IOException;
import java.util.Map;

class ScraperEHMSWebClient {
    private static final String NOT_LOGGED_IN = "Logowanie do systemu";
    private static final String RATE_LIMITED = "Przepisz kod z obrazka";
    private static final String PHP_SESS_ID = "dsysPHPSESSID";

    private final Logger logger = LoggerFactory.getLogger(ScraperEHMSWebClient.class);

    Document getRequest(EHMSUser user, ScraperEHMSUrl url) throws IOException {
        if (user.getSessionToken().isEmpty()) {
            logger.debug("User {} didn't have a session token attached, trying to log in...", user.getUsername());
            login(user);
            return getRequest(user, url);
        }

        if (user.getStudyID().isPresent() || user.getUserID().isPresent()) {
            logger.warn("""
                    User {} was previously logged in via a different PK4J implementation.
                    Please, DO NOT reuse the same user object across different implementations of PK4J, to avoid unnecessary logins.
                    Trying to log in..""", user.getUsername());
            user.setUserID(null);
            user.setStudyID(null);
            login(user);
            return getRequest(user, url);
        }

        logger.debug("Trying to retrieve data from {} for user {} ...", url.value(), user.getUsername());
        Connection.Response response = Jsoup.connect(url.value()).cookie(PHP_SESS_ID, user.getSessionToken().get()).execute();

        checkStatusCode(response);

        Document responseBody = response.parse();

        if (!responseBody.getElementsContainingText(NOT_LOGGED_IN).isEmpty()) {
            logger.debug("User {}'s session token was inactive, trying to log back in...", user.getUsername());
            login(user);
            return getRequest(user, url);
        }

        logger.debug("Successfully retrieved data from {}, for user {}", url.value(), user.getUsername());
        return responseBody;
    }

    private void login(EHMSUser user) throws IOException {
        logger.debug("Trying to retrieve a login form from EHMS...");
        Connection.Response response = Jsoup.connect(ScraperEHMSUrl.BASE.value()).execute();

        checkStatusCode(response);

        logger.debug("Successfully retrieved the login form");
        Document responseBody = response.parse();

        Map<String, String> cookies = response.cookies();
        Map<String, String> formData = prepareLoginFormData(responseBody, user.getUsername(), user.getPassword());

        logger.debug("Trying to log in as user {} ...", user.getUsername());

        Connection.Response loginResponse = Jsoup.connect(ScraperEHMSUrl.BASE.value()).data(formData).cookies(cookies).method(Connection.Method.POST).execute();
        checkStatusCode(loginResponse);

        Document loginResponseBody = loginResponse.parse();

        if (loginResponseBody.getElementsContainingText(NOT_LOGGED_IN).isEmpty()) {
            logger.debug("Successfully logged in as user {}", user.getUsername());
            user.setSessionToken(cookies.get(PHP_SESS_ID));
        } else {
            if (!loginResponseBody.getElementsContainingText(RATE_LIMITED).isEmpty()) {
                logger.warn("User {} got rate-limited! Please wait or solve the captcha on a different device before trying to log in again!", user.getUsername());
                throw new RateLimitExceededException(user.getUsername());
            } else {
                logger.warn("Authentication for user {} failed, are the login details correct?", user.getUsername());
                throw new IllegalArgumentException(String.format("Authentication failed for user %s, are the login details correct?", user.getUsername()));
            }
        }
    }

    private Map<String, String> prepareLoginFormData(Document document, String login, String password) {
        Elements form = document.getElementsByClass("form-control");

        String loginFieldName = form.get(0).attr("name");
        String passwordFieldName = form.get(1).attr("name");
        String counterFieldName = document.select("input[type=hidden]").get(1).val();

        return Map.of(loginFieldName, login, passwordFieldName, password, "log_form", "yes", "counter", counterFieldName);
    }

    private void checkStatusCode(Connection.Response response) {
        if (response.statusCode() != 200) {
            logger.error("EHMS returned an unexpected status code: {}", response.statusCode());
            throw new EHMSException(response.statusCode());
        }
    }
}
