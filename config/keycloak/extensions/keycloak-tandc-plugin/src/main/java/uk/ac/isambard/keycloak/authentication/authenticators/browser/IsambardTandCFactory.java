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

public class IsambardTandCFactory implements AuthenticatorFactory {
    public static final String PROVIDER_ID = "isambard-tandc";
    public static final IsambardTandC SINGLETON = new IsambardTandC();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Isambard Terms and Conditions";
    }

    @Override
    public String getHelpText() {
        return "Validates acceptance of the Isambard Terms and Conditions";
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
        property.setName("tandc.link");
        property.setLabel("URL/Link to the terms and conditions");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("URL/Link pointing to the terms and conditions.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("tandc.last_updated");
        property.setLabel("Last updated date of the terms and conditions");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("The last date the terms and conditions were updated, " +
                             "in ISO 8601 format (YYYY-MM-DD), e.g. 2024-01-31.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("tandc.required_seconds");
        property.setLabel("Amount of time in seconds needed to read the terms and conditions");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText(
                "The number of whole seconds the user must spend before accepting the terms and conditions.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("dpriv.link");
        property.setLabel("URL/Link to the data privacy policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("URL/Link pointing to the data privacy policy.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("dpriv.last_updated");
        property.setLabel("Last updated date of the data privacy policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("The last date the data privacy policy was updated, " +
                             "in ISO 8601 format (YYYY-MM-DD), e.g. 2024-01-31.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("dpriv.required_seconds");
        property.setLabel("Amount of time in seconds needed to read the data privacy policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText(
                "The number of whole seconds the user must spend before accepting the data privacy policy.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("ause.link");
        property.setLabel("URL/Link to the acceptable use policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("URL/Link pointing to the acceptable use policy.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("ause.last_updated");
        property.setLabel("Last updated date of the acceptable use policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("The last date the acceptable use policy was updated, " +
                             "in ISO 8601 format (YYYY-MM-DD), e.g. 2024-01-31.");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("ause.required_seconds");
        property.setLabel("Amount of time in seconds needed to read the acceptable use policy");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText(
                "The number of whole seconds the user must spend before accepting the acceptable use policy.");
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