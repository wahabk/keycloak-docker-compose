package uk.ac.isambard.keycloak.protocol.oidc.mappers;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.representations.IDToken;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.util.JsonSerialization;

import org.keycloak.provider.ProviderConfigProperty;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * OIDC Protocol Mapper that fetches user project and resource information from the Waldur API and adds it as claims to tokens at issuance time.
 *
 * This mapper is based on Keycloak's built-in protocol mappers (e.g. UserAttributeMapper, AudienceProtocolMapper) and follows the same pattern of extending AbstractOIDCProtocolMapper.
 * Authenticator blocks login if user not authorised; mapper falls back to cached attributes as user is already logged in
 */
public class BricsProjectGroupMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final Logger logger = Logger.getLogger(BricsProjectGroupMapper.class);

    public static final String PROVIDER_ID = "brics-project-group-mapper";

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

    // Keep in sync with SelectActiveGroupRequiredAction in keycloak-isambard-group-selector.
    private static final String SELECTED_GROUP_ATTRIBUTE_PREFIX = "selected-group:";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        ProviderConfigProperty property;
        
        property = new ProviderConfigProperty();
        property.setName("waldur.api.url");
        property.setLabel("Waldur API URL");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("URL of the Waldur API to use to check authorisation.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("waldur.api.key");
        property.setLabel("Waldur API Key");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Key used to authenticate with the Waldur API.");
        configProperties.add(property);

        // This adds the "Add to access token", "Add to ID token", and "Add to userinfo" checkboxes
        // Pattern used by all built-in Keycloak mappers
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, BricsProjectGroupMapper.class);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "BriCS projects → groups mapper";
    }

    @Override
    public String getHelpText() {
        return "Fetches user projects from Waldur API at token issuance time and sets the groups claim";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    protected AuthorisationStatus checkEmailHasAccess(KeycloakSession session,
                                                      String email,
                                                      String waldur_api_url,
                                                      String waldur_api_key) {

        AuthorisationStatus status = new AuthorisationStatus();

        if (email == null || waldur_api_url == null || waldur_api_key == null) {
            return status;
        }

        // Call the Waldur API to check authorisation and retrieve project information
        try {
            SimpleHttp.Response response = SimpleHttp.doGet(waldur_api_url, session)
                    .header("Authorization", "Token " + waldur_api_key)
                    .param("email", email)
                    .asResponse();

            if (response.getStatus() != 200) {
                logger.warn("API call failed:  " + response.getStatus());
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

    /**
     * Main protocol mapper method called by Keycloak during token generation.
     * AbstractOIDCProtocolMapper.transformAccessToken/transformIDToken/transformUserInfoToken
     * delegate to this method based on the token type being generated.
     */
    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession, 
                          KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx) {

        UserModel user = userSession.getUser();
        String email = user.getEmail();

        if (email == null) {
            logger.warn("User " + user.getUsername() + " has no email address, cannot fetch projects.");
            return;
        }

        // Get mapper configuration - these are the values set in the Keycloak admin UI
        String waldur_api_url = mappingModel.getConfig().get("waldur.api.url");
        String waldur_api_key = mappingModel.getConfig().get("waldur.api.key");

        if (waldur_api_url == null || waldur_api_key == null) {
            logger.error("Waldur API URL or Key not configured in mapper.");
            return;
        }

        // Fetch fresh authorisation status from Waldur API
        AuthorisationStatus access = checkEmailHasAccess(keycloakSession, email, waldur_api_url, waldur_api_key);

        if (access.status.equals("active")) {
            // User is active - process their projects
            ArrayList<String> groups = new ArrayList();
            for (Map.Entry<String, ProjectInfo> entry : access.projects.entrySet()) {
                groups.add(entry.getKey());
            }

            // If the user has selected a single active group for this client (see
            // keycloak-isambard-group-selector), only return that one. Non-interactive
            // flows (service accounts, direct grant) never make a selection, so they
            // keep seeing every group, as before.
            String clientId = clientSessionCtx.getClientSession().getClient().getClientId();
            String selectedGroup = user.getFirstAttribute(SELECTED_GROUP_ATTRIBUTE_PREFIX + clientId);

            if (selectedGroup != null && groups.contains(selectedGroup)) {
                groups = new ArrayList<>(Arrays.asList(selectedGroup));
            }

            logger.info("[GROUP MAPPER] " + email + " fetched with projects " + groups);

            token.getOtherClaims().put("groups", groups);

        } else {
            // User is not active - use cached attributes if available
            logger.warn("[GROUP MAPPER] " + email + " is not active (status:  " + access.status + ")");
        }
    }
}
