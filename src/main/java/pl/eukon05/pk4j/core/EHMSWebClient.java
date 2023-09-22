package pl.eukon05.pk4j.core;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.eukon05.pk4j.exception.EHMSException;
import pl.eukon05.pk4j.exception.RateLimitExceededException;
import pl.eukon05.pk4j.exception.UserAlreadyLoggedInException;

import java.io.IOException;
import java.util.Map;

class EHMSWebClient {
    private EHMSWebClient() {
    }

    private static final String AUTH_CHECK = "Logowanie do systemu";
    private static final Logger logger = LoggerFactory.getLogger(EHMSWebClient.class);

    static Document getRequest(EHMSUrl url, EHMSUser user) throws IOException {
        logger.debug("Trying to retrieve data from {} for user {} ...", url.value(), user.login);
        Connection.Response response = Jsoup.connect(url.value()).cookies(user.cookies).execute();

        if (response.statusCode() != 200) {
            logger.error("EHMS returned an unexpected status code: {}", response.statusCode());
            throw new EHMSException(response.statusCode());
        }

        Document responseBody = response.parse();

        if (!responseBody.getElementsContainingText(AUTH_CHECK).isEmpty()) {
            logger.debug("User {} didn't have an active session, trying to log in...", user.login);
            login(user);
            return getRequest(url, user);
        }

        logger.debug("Successfully retrieved data from {}, for user {}", url.value(), user.login);
        return responseBody;
    }

    private static void login(EHMSUser user) throws IOException {
        logger.debug("Trying to retrieve a login form from EHMS...");
        Connection.Response response = Jsoup.connect(EHMSUrl.BASE.value()).execute();

        if (response.statusCode() != 200) {
            logger.error("EHMS returned an unexpected status code: {}", response.statusCode());
            throw new EHMSException(response.statusCode());
        }

        logger.debug("Successfully retrieved the login form");
        Document document = response.parse();

        Map<String, String> cookies = response.cookies();

        Elements inputs = document.getElementsByClass("form-control");

        String loginForm = inputs.get(0).attr("name");
        String passForm = inputs.get(1).attr("name");
        String counter = document.select("input[type=hidden]").get(1).val();

        Map<String, String> data = Map.of(loginForm, user.login, passForm, user.password, "log_form", "yes", "counter", counter);

        logger.debug("Trying to log in as user {} ...", user.login);

        Document result = Jsoup.connect(EHMSUrl.BASE.value()).data(data).cookies(cookies).post();

        if (result.getElementsContainingText(AUTH_CHECK).isEmpty()) {
            logger.debug("Successfully logged in as user {}", user.login);
            user.cookies = cookies;
        } else {
            if (!result.getElementsContainingText("Przepisz kod z obrazka").isEmpty()) {
                logger.warn("User {} got rate-limited! Please wait or solve the captcha on a different device before trying to log in again!", user.login);
                throw new RateLimitExceededException(user.login);
            } else if (!result.getElementsContainingText("Wykryto podwójne zalogowanie").isEmpty()) {
                logger.warn("User {} is already logged in on another device! Please logout from other devices before trying to log in again!", user.login);
                throw new UserAlreadyLoggedInException(user.login);
            } else {
                logger.warn("Authentication for user {} failed, are the login details correct?", user.login);
                throw new IllegalArgumentException(String.format("Authentication failed for user %s, are the login details correct?", user.login));
            }
        }
    }

}
