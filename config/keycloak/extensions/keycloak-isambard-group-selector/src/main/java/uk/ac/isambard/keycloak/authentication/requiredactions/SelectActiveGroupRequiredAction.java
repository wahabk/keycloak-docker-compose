package uk.ac.isambard.keycloak.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.util.JsonSerialization;

import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a user pick exactly one of their Waldur groups/projects to be active for a
 * given client. Runs as a required action so it is re-evaluated on every new
 * client authorization (see evaluateTriggers), even when the user already has a
 * valid SSO session - see config/keycloak/RequiredAction.md for why this needed
 * to be a RequiredActionProvider rather than an Authenticator or consent screen.
 *
 * Implements both RequiredActionProvider and RequiredActionFactory in one class
 * (create() returns "this"), matching Keycloak's own built-ins such as
 * TermsAndConditions and UpdatePassword - the provider is stateless, so a
 * separate factory file is unnecessary boilerplate.
 *
 * The selection is stored as a user attribute keyed by client id (not a session
 * note), since RequiredActionContext has no access to the AuthenticatedClientSessionModel
 * (it doesn't exist yet at this point in the flow) or to the UserSessionModel.
 * BricsProjectGroupMapper/IsambardProtocolMapper must use the exact same
 * attribute key prefix when reading this back at token-build time.
 *
 * Calls the Waldur API directly, configured the same way as IsambardAuthenticator
 * (per-required-action config, not per-mapper), rather than trusting the cached
 * "projects" user attribute: required actions run before IsambardProtocolMapper
 * refreshes that cache in this same flow, and IsambardAuthenticator doesn't run
 * at all on SSO-cookie reuse, so the cache can otherwise be a full login stale.
 */
public class SelectActiveGroupRequiredAction implements RequiredActionProvider, RequiredActionFactory {

    private static final Logger logger = Logger.getLogger(SelectActiveGroupRequiredAction.class);

    public static final String PROVIDER_ID = "select-active-group";

    // Keep in sync with BricsProjectGroupMapper/IsambardProtocolMapper.
    public static final String ATTRIBUTE_PREFIX = "selected-group:";

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
        public Map<String, ProjectInfo> projects = new LinkedHashMap<>();
        public String invited_by = "";
        public String reason = "";
    }

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        ProviderConfigProperty property;

        property = new ProviderConfigProperty();
        property.setName("waldur.api.url");
        property.setLabel("Waldur API URL");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("URL of the Waldur API to use to fetch the user's projects.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("waldur.api.key");
        property.setLabel("Waldur API Key");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Key used to authenticate with the Waldur API.");
        configProperties.add(property);
    }

    // ----- RequiredActionFactory -----

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Select Active Group";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return configProperties;
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    // ----- RequiredActionProvider -----

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    // Set to "true" on a client (Advanced > Attributes) to opt that client into
    // group selection. Absent/anything else means the client keeps seeing every
    // group/project, unchanged - this is what lets us enable the required action
    // realm-wide (a realm-level toggle is all Keycloak offers) while only a
    // subset of clients actually trigger the picker.
    private static final String CLIENT_ENABLED_ATTRIBUTE = "group-selector.enabled";

    private static boolean isEnabledForClient(ClientModel client) {
        return Boolean.parseBoolean(client.getAttribute(CLIENT_ENABLED_ATTRIBUTE));
    }

    private static String attributeKey(ClientModel client) {
        return ATTRIBUTE_PREFIX + client.getClientId();
    }

    /**
     * Fetches the user's current projects from Waldur, falling back to the
     * cached "projects" user attribute if the API isn't configured or fails.
     */
    private Map<String, String> loadProjects(RequiredActionContext context) {
        UserModel user = context.getUser();
        Map<String, String> projects = new LinkedHashMap<>();

        if (user == null || user.getEmail() == null) {
            return projects;
        }

        RequiredActionConfigModel config = context.getConfig();
        String waldurApiUrl = config == null ? null : config.getConfigValue("waldur.api.url");
        String waldurApiKey = config == null ? null : config.getConfigValue("waldur.api.key");

        if (waldurApiUrl == null || waldurApiKey == null) {
            logger.warn("Waldur API URL or Key not configured for Select Active Group required action.");
            return loadCachedProjects(user);
        }

        try {
            SimpleHttp.Response response = SimpleHttp.doGet(waldurApiUrl, context.getSession())
                    .header("Authorization", "Token " + waldurApiKey)
                    .param("email", user.getEmail())
                    .asResponse();

            if (response.getStatus() != 200) {
                logger.warn("Waldur API call failed: " + response.getStatus());
                return loadCachedProjects(user);
            }

            AuthorisationStatus access = response.asJson(AuthorisationStatus.class);

            if (!"active".equals(access.status)) {
                return projects;
            }

            for (Map.Entry<String, ProjectInfo> entry : access.projects.entrySet()) {
                String name = entry.getValue() != null && entry.getValue().name != null && !entry.getValue().name.isEmpty()
                        ? entry.getValue().name
                        : entry.getKey();
                projects.put(entry.getKey(), name);
            }

            return projects;
        } catch (Exception e) {
            logger.warn("Waldur API call failed: " + e.getMessage());
            return loadCachedProjects(user);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadCachedProjects(UserModel user) {
        Map<String, String> projects = new LinkedHashMap<>();
        String cachedProjects = user.getFirstAttribute("projects");

        if (cachedProjects == null) {
            return projects;
        }

        try {
            Map<String, Object> parsed = JsonSerialization.readValue(cachedProjects, Map.class);

            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String name = entry.getKey();

                if (entry.getValue() instanceof Map) {
                    Object nameValue = ((Map<?, ?>) entry.getValue()).get("name");
                    if (nameValue != null) {
                        name = nameValue.toString();
                    }
                }

                projects.put(entry.getKey(), name);
            }
        } catch (Exception e) {
            logger.warn("Could not parse cached projects for " + user.getUsername() + ": " + e.getMessage());
        }

        return projects;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        UserModel user = context.getUser();
        ClientModel client = context.getAuthenticationSession().getClient();

        if (user == null || client == null || !isEnabledForClient(client)) {
            return;
        }

        Map<String, String> projects = loadProjects(context);
        String attributeKey = attributeKey(client);
        String selected = user.getFirstAttribute(attributeKey);

        if (selected != null && projects.containsKey(selected)) {
            // already have a valid selection for this client, nothing to do
            return;
        }

        if (projects.size() > 1) {
            context.getAuthenticationSession().addRequiredAction(PROVIDER_ID);
            return;
        }

        // 0 or 1 groups - nothing to choose, so decide silently without prompting
        if (projects.size() == 1) {
            user.setSingleAttribute(attributeKey, projects.keySet().iterator().next());
        } else if (selected != null) {
            // the previously selected group is no longer valid (e.g. access revoked)
            user.removeAttribute(attributeKey);
        }
    }

    private void challenge(RequiredActionContext context, String selected, String error) {
        Map<String, String> projects = loadProjects(context);

        LoginFormsProvider form = context.form()
                .setAttribute("projects", projects)
                .setAttribute("selected", selected);

        if (error != null) {
            form.setError(error);
        }

        context.challenge(form.createForm("select-group.ftl"));
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        ClientModel client = context.getAuthenticationSession().getClient();
        String current = client == null ? null : context.getUser().getFirstAttribute(attributeKey(client));
        challenge(context, current, null);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        UserModel user = context.getUser();
        ClientModel client = context.getAuthenticationSession().getClient();

        if (user == null || client == null) {
            context.failure();
            return;
        }

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String attributeKey = attributeKey(client);

        if (formData != null && formData.containsKey("cancel")) {
            if (user.getFirstAttribute(attributeKey) != null) {
                // only allow cancelling if there is already a selection to fall back to
                // (e.g. an on-demand "switch group" via kc_action)
                context.cancel();
                return;
            }

            challenge(context, null, "You must select a group to continue.");
            return;
        }

        String selected = formData == null ? null : formData.getFirst("group");
        Map<String, String> projects = loadProjects(context);

        // never trust the posted value - it must be one of the user's real projects
        if (selected == null || !projects.containsKey(selected)) {
            logger.warn(user.getUsername() + " tried to select an invalid/unauthorised group: " + selected);
            challenge(context, selected, "Please select one of your available groups.");
            return;
        }

        user.setSingleAttribute(attributeKey, selected);
        context.success();
    }
}
