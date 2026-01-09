package uk.ac.isambard.keycloak.authentication.authenticators.browser;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.util.JsonSerialization;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public class IsambardAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(IsambardAuthenticator.class);

    /**
     * Converts a standard POSIX Shell globbing pattern into a regular expression
     * pattern. The result can be used with the standard {@link java.util.regex} API to
     * recognize strings which match the glob pattern.
     * <p/>
     * See also, the POSIX Shell language:
     * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
     *
     * @param pattern A glob pattern.
     * @return A regex pattern to recognize the given glob pattern.
     */
    private static final String convertGlobToRegex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length());
        int inGroup = 0;
        int inClass = 0;
        int firstIndexInClass = -1;
        char[] arr = pattern.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            switch (ch) {
                case '\\':
                    if (++i >= arr.length) {
                        sb.append('\\');
                    } else {
                        char next = arr[i];
                        switch (next) {
                            case ',':
                                // escape not needed
                                break;
                            case 'Q':
                            case 'E':
                                // extra escape needed
                                sb.append('\\');
                            default:
                                sb.append('\\');
                        }
                        sb.append(next);
                    }
                    break;
                case '*':
                    if (inClass == 0)
                        sb.append(".*");
                    else
                        sb.append('*');
                    break;
                case '?':
                    if (inClass == 0)
                        sb.append('.');
                    else
                        sb.append('?');
                    break;
                case '[':
                    inClass++;
                    firstIndexInClass = i+1;
                    sb.append('[');
                    break;
                case ']':
                    inClass--;
                    sb.append(']');
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    if (inClass == 0 || (firstIndexInClass == i && ch == '^'))
                        sb.append('\\');
                    sb.append(ch);
                    break;
                case '!':
                    if (firstIndexInClass == i)
                        sb.append('^');
                    else
                        sb.append('!');
                    break;
                case '{':
                    inGroup++;
                    sb.append('(');
                    break;
                case '}':
                    inGroup--;
                    sb.append(')');
                    break;
                case ',':
                    if (inGroup > 0)
                        sb.append('|');
                    else
                        sb.append(',');
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static class ResourceInfo {
        public String name = "";
        public String username = "";
    }

    private static class ProjectInfo {
        public String name = "";
        public ArrayList<ResourceInfo> resources = new ArrayList<>();
    }

    private static class AuthorisationStatus {
        public String email = "";
        public String status = "";
        public String short_name = "";
        public HashMap<String, ProjectInfo> projects = new HashMap<>();
        public String invited_by = "";
        public String reason = "";
    }

    @Override
    public void close() {
    }

    protected AuthorisationStatus checkEmailHasAccess(KeycloakSession session,
                                                      String email,
                                                      String waldur_api_url,
                                                      String waldur_api_key) {

        AuthorisationStatus status = new AuthorisationStatus();

        if (email == null || waldur_api_url == null || waldur_api_key == null) {
            return status;
        }

        // Check if the email has access to the Waldur API.
        // Call the Waldur API endpoint at waldur_api_url, authenticating
        // with the waldur_api_key, and pass the email as the argument.
        // This will return a boolean value indicating whether the email
        // has access to the Waldur API.

        // Call the Waldur API
        try {
            SimpleHttp.Response response = SimpleHttp.doGet(waldur_api_url, session)
                    .header("Authorization", "Token " + waldur_api_key)
                    .param("email", email)
                    .asResponse();

            if (response.getStatus() != 200) {
                logger.warn("API call failed: " + response.getStatus());
                logger.warn("API call failed: " + response.asString());
                status.reason = "API call to Waldur failed";
                return status;
            }

            try {
                status = response.asJson(AuthorisationStatus.class);
                return status;
            }
            catch (Exception e) {
                logger.warn("Decoding API response failed: " + e.getMessage());
                logger.warn(response.asString());
                status.reason = "API call to Waldur failed";
                return status;
            }
        }
        catch (Exception e) {
            logger.warn("API call failed: " + e.getMessage());
            status.reason = "API call to Waldur failed";
            return status;
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // context.success();
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        AuthenticatorConfigModel config = context.getAuthenticatorConfig();

        String support_email = "unknown";

        if (config != null) {
            // check if the user is in a group that skips the email check
            String groups = config.getConfig().get("allowed.groups");

            if (groups != null) {
                for (String g : groups.split(";")) {
                    for (String ug : user.getGroupsStream().map(gm -> gm.getName()).toArray(String[]::new)) {
                        // remove leading and trailing whitespace
                        ug = ug.trim();
                        if (g.equals(ug)) {
                            logger.info("[LOGIN SUCCESS] User " + user.getEmail() + " is in group " + g + " and is allowed to login.");
                            context.success();
                            return;
                        }
                    }
                }
            }

            support_email = config.getConfig().get("support.email");

            if (support_email == null) {
                support_email = "unknown";
            }
        }

        // Access is not only allowed to authorised emails
        String email = user.getEmail();

        if (email == null) {
            logger.warn("[LOGIN FAILED] User " + user.getUsername() + " has no email address, so cannot log in!");
            Response challenge = context.form()
                    .setAttribute("supportEmail", support_email)
                    .createForm("email-is-null.ftl");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        if (config != null) {
            // remove leading and trailing whitespace and lowercase
            String sanitised_email = email.toLowerCase().trim();

            // check if this is one of the banned emails
            String emails = config.getConfig().get("banned.emails");

            // Split the emails by semicolon
            if (emails != null) {
                for (String e : emails.split(";")) {
                    // remove leading and trailing whitespace and lowercase
                    e = e.toLowerCase().trim();
                    if (e.equals(sanitised_email)) {
                        logger.warn("[LOGIN FAILED] " + email + " is banned from logging in.");
                        Response challenge = context.form()
                                .setAttribute("email", email)
                                .setAttribute("supportEmail", support_email)
                                .createForm("email-is-banned.ftl");
                        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                        return;
                    }
                }
            }

            emails = config.getConfig().get("allowed.emails");

            // Split the emails by semicolon
            if (emails != null) {
                for (String e : emails.split(";")) {
                    // remove leading and trailing whitespace and lowercase
                    e = e.toLowerCase().trim();
                    if (e.equals(sanitised_email)) {
                        logger.info("[LOGIN SUCCESS] " + email + " is directly allowed to log in.");
                        context.success();
                        return;
                    }
                }
            }

            String waldur_api_url = config.getConfig().get("waldur.api.url");
            String waldur_api_key = config.getConfig().get("waldur.api.key");

            AuthorisationStatus access = checkEmailHasAccess(context.getSession(), email, waldur_api_url, waldur_api_key);

            if (access.status.equals("active"))
            {
                // trim the short name
                String short_name = access.short_name.trim();
                HashMap<String, ProjectInfo> projects = access.projects;

                // Check if the projects have changed - we will JSON encode the projects
                // and compare them as strings
                // String projects_json = new JSONObject(projects).toString();
                String projects_json = "{}";

                try {
                    projects_json = JsonSerialization.writeValueAsString(projects);
                } catch (Exception e) {
                    logger.warn("Error serialising projects to JSON: " + e.getMessage());
                }

                if (short_name == null || short_name == "" || short_name.length() == 0
                        || short_name.toLowerCase() == "none") {
                    logger.info("[LOGIN SUCCESS] " + email
                            + " is authorised to log in, but they have not set their short name.");
                    short_name = "";

                    // set their projects to null, as they won't be able to access them
                    projects = new HashMap<>();
                    try {
                        projects_json = JsonSerialization.writeValueAsString(projects);
                    } catch (Exception e) {
                        logger.warn("Error serialising projects to JSON: " + e.getMessage());
                    }
                } else if (short_name.length() > 128) {
                    // this is a serious failure - short names should be <= 64 characters...
                    logger.warn("[LOGIN FAILED] " + email + " is authorised to log in, but their short name is too long.");
                    Response challenge = context.form()
                            .setAttribute("email", email)
                            .setAttribute("reason", "Internal error")
                            .setAttribute("supportEmail", support_email)
                            .createForm("email-not-authorised.ftl");
                    context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                    return;
                } else {
                    logger.info("[LOGIN SUCCESS] " + email + " is authorised to log in with short name " + short_name
                            + " and projects " + projects_json);
                }

                // Get the current user attributes and see if anything needs to change
                Map<String, List<String>> userAttributes = user.getAttributes();

                // Check if the short name has changed
                if (userAttributes.containsKey("short_name")) {
                    if (!userAttributes.get("short_name").get(0).equals(short_name)) {
                        user.setSingleAttribute("short_name", short_name);
                    }
                } else {
                    user.setSingleAttribute("short_name", short_name);
                }

                if (userAttributes.containsKey("projects")) {
                    if (!userAttributes.get("projects").get(0).equals(projects_json)) {
                        user.setSingleAttribute("projects", projects_json);
                    }
                } else {
                    user.setSingleAttribute("projects", projects_json);
                }

                context.success();
                return;
            } else if (access.status.equals("invited")) {
                // invitation that is pending... check that the email
                // is in a group that is allowed to login
                String invitable_domains = config.getConfig().get("invitable.domains");
                String uninvitable_domains = config.getConfig().get("uninvitable.domains");

                // clear the custom attributes for this user
                user.removeAttribute("short_name");
                user.removeAttribute("projects");

                // extract the domain from the email
                String domain = email.substring(email.indexOf("@") + 1);

                // check if the domain is in the uninvitable domains
                if (uninvitable_domains != null) {
                    for (String d : uninvitable_domains.split(";")) {
                        try {
                            // convert the globbed d to a regex
                            d = convertGlobToRegex(d);
                            if (domain.matches(d)) {
                                logger.warn("[LOGIN FAILED] " + email + " needs review to log in from matched domain "
                                        + domain);
                                Response challenge = context.form()
                                        .setAttribute("email", email)
                                        .setAttribute("supportEmail", support_email)
                                        .setAttribute("inviter", access.invited_by)
                                        .createForm("email-is-pending.ftl");
                                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                                return;
                            }
                        } catch (Exception e) {
                            logger.error("Error converting glob to regex: " + e.getMessage());
                        }
                    }
                }

                // check if the domain is in the invitable domains
                if (invitable_domains != null) {
                    for (String d : invitable_domains.split(";")) {
                        try {
                            // convert the globbed d to a regex
                            d = convertGlobToRegex(d);
                            if (domain.matches(d)) {
                                logger.info("[LOGIN SUCCESS] " + email
                                        + " is allowed to log in when invited from matched domain " + domain);
                                context.success();
                                return;
                            }
                        } catch (Exception e) {
                            logger.error("Error converting glob to regex: " + e.getMessage());
                        }
                    }
                }

                // anything that hasn't matched so far is not allowed
                logger.warn("[LOGIN FAILED] " + email + " needs review to log in from unmatched domain " + domain);
                Response challenge = context.form()
                        .setAttribute("email", email)
                        .setAttribute("supportEmail", support_email)
                        .setAttribute("inviter", access.invited_by)
                        .createForm("email-is-pending.ftl");
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                return;
            }
            else {
                logger.warn("[LOGIN FAILED] " + email + " is not authorised to log in.");
                user.removeAttribute("short_name");
                user.removeAttribute("projects");

                Response challenge = context.form()
                        .setAttribute("email", email)
                        .setAttribute("reason", access.reason)
                        .setAttribute("supportEmail", support_email)
                        .createForm("email-not-authorised.ftl");
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                return;
            }
        }

        // we haven't found the email and can't confirm with Waldur
        // that the email is authorised
        logger.warn("[LOGIN FAILED] " + email + " is not authorised to log in (no API check).");

        Response challenge = context.form()
                .setAttribute("email", email)
                .setAttribute("supportEmail", support_email)
                .setAttribute("reason", "Not in an approved list")
                .createForm("email-not-authorised.ftl");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

}