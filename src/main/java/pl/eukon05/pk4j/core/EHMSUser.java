package pl.eukon05.pk4j.core;

import java.util.Collections;
import java.util.Map;

public class EHMSUser {
    final String login;
    final String password;
    Map<String, String> cookies = Collections.emptyMap();

    private EHMSUser(String login, String password) {
        if (login == null || password == null)
            throw new NullPointerException();

        if (login.isBlank() || password.isBlank())
            throw new IllegalArgumentException("Credentials cannot be empty strings!");

        this.login = login;
        this.password = password;
    }

    public static EHMSUser fromCredentials(String login, String password) {
        return new EHMSUser(login, password);
    }
}
