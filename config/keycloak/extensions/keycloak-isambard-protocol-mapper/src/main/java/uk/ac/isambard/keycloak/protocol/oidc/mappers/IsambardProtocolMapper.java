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
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * OIDC Protocol Mapper that fetches user project and resource information from the Waldur API and adds it as claims to tokens at issuance time.
 * 
 * This mapper is based on Keycloak's built-in protocol mappers (e.g. UserAttributeMapper, AudienceProtocolMapper) and follows the same pattern of extending AbstractOIDCProtocolMapper. 
 * Authenticator blocks login if user not authorised; mapper falls back to cached attributes as user is already logged in
 */
public class IsambardProtocolMapper extends AbstractOIDCProtocolMapper 
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final Logger logger = Logger.getLogger(IsambardProtocolMapper.class);

    public static final String PROVIDER_ID = "isambard-protocol-mapper";

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
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, IsambardProtocolMapper.class);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Isambard Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Fetches user projects and short_name from Waldur API at token issuance time";
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
            logger.warn("Waldur API URL or Key not configured in mapper.");
            // Fall back to cached user attributes if API is not configured
            String cachedShortName = user.getFirstAttribute("short_name");
            String cachedProjects = user.getFirstAttribute("projects");
            
            if (cachedShortName != null) {
                token.getOtherClaims().put("short_name", cachedShortName);
            }
            if (cachedProjects != null) {
                token.getOtherClaims().put("projects", cachedProjects);
            }
            return;
        }

        // Fetch fresh authorisation status from Waldur API
        AuthorisationStatus access = checkEmailHasAccess(keycloakSession, email, waldur_api_url, waldur_api_key);

        if (access.status.equals("active")) {
            // User is active - process their short name and projects
            String short_name = access.short_name.trim();
            HashMap<String, ProjectInfo> projects = access.projects;

            // Serialize projects to JSON for storage
            String projects_json = "{}";

            try {
                projects_json = JsonSerialization.writeValueAsString(projects);
            } catch (Exception e) {
                logger.warn("Error serialising projects to JSON: " + e.getMessage());
            }

            if (short_name == null || short_name == "" || short_name.length() == 0
                    || short_name.toLowerCase().equals("none")) {
                logger.info("[TOKEN MAPPER] " + email
                        + " is authorised, but they have not set their short name.");
                short_name = "";

                // Set their projects to empty, as they won't be able to access them without a short name
                projects = new HashMap<>();
                try {
                    projects_json = JsonSerialization.writeValueAsString(projects);
                } catch (Exception e) {
                    logger.warn("Error serialising projects to JSON: " + e.getMessage());
                }
            } else if (short_name.length() > 128) {
                // Short names should be <= 64 characters, so 128 is a serious failure
                logger.warn("[TOKEN MAPPER] " + email + " short name is too long.");
                short_name = "";
                projects = new HashMap<>();
                try {
                    projects_json = JsonSerialization.writeValueAsString(projects);
                } catch (Exception e) {
                    logger.warn("Error serialising projects to JSON: " + e.getMessage());
                }
            } else {
                logger.info("[TOKEN MAPPER] " + email + " fetched with short name " + short_name
                        + " and projects " + projects_json);
            }

            // Update user attributes if they have changed
            // These are cached on the user object for fallback when API is unavailable
            Map<String, List<String>> userAttributes = user.getAttributes();

            if (userAttributes.containsKey("short_name")) {
                if (! userAttributes.get("short_name").get(0).equals(short_name)) {
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

            // Add claims to the token
            token.getOtherClaims().put("short_name", short_name);
            token.getOtherClaims().put("projects", projects_json);
            
        } else {
            // User is not active - use cached attributes if available
            logger.warn("[TOKEN MAPPER] " + email + " is not active (status:  " + access.status + ")");
            
            String cachedShortName = user.getFirstAttribute("short_name");
            String cachedProjects = user.getFirstAttribute("projects");
            
            if (cachedShortName != null) {
                token.getOtherClaims().put("short_name", cachedShortName);
            }
            if (cachedProjects != null) {
                token.getOtherClaims().put("projects", cachedProjects);
            }
        }
    }
}