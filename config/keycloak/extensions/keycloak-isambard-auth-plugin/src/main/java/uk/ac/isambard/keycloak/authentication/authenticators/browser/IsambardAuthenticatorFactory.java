package uk.ac.isambard.keycloak.authentication.authenticators.browser;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;
import java.util.ArrayList;

public class IsambardAuthenticatorFactory implements AuthenticatorFactory {
    public static final String PROVIDER_ID = "isambard-authenticator";
    public static final IsambardAuthenticator SINGLETON = new IsambardAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Isambard Authentication";
    }

    @Override
    public String getHelpText() {
        return "Authenticates the user with extra Isambard options";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED,
        };
    }

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName("support.email");
        property.setLabel("Support Email");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Email address to contact for support.");
        configProperties.add(property);

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

        property = new ProviderConfigProperty();
        property.setName("banned.emails");
        property.setLabel("Banned Emails");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Semicolon-separated list of email addresses that should never be allowed to login.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("allowed.emails");
        property.setLabel("Allowed Emails");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Semicolon-separated list of email addresses that should always be allowed to login.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("allowed.groups");
        property.setLabel("Allowed Groups");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Semicolon-separated list of groups that should always be allowed to login.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("invitable.domains");
        property.setLabel("Invitable Domains");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Semicolon-separated list of email domains (glob) that that users can be invited from.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("uninvitable.domains");
        property.setLabel("Uninvitable Domains");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText(
                "Semicolon-separated list of email domains (glob) that that users cannot be invited from.");
        configProperties.add(property);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
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

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }
}