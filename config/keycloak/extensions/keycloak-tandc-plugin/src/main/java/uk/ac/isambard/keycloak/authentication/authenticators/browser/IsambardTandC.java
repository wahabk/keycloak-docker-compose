package uk.ac.isambard.keycloak.authentication.authenticators.browser;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.cache.UserCache;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IsambardTandC implements Authenticator {

    private static final Logger logger = Logger.getLogger(IsambardTandC.class);

    private enum AcceptedState {
        ACCEPTED,
        NOT_ACCEPTED,
        NEW_VERSION
    };

    private static class TandCItem {
        private String key;
        private String type;
        private String link;
        private LocalDateTime start_time;
        private LocalDateTime last_updated;
        private LocalDateTime last_accepted;
        private int required_seconds;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public TandCItem(
                @JsonProperty("key") String key,
                @JsonProperty("type") String type,
                @JsonProperty("link") String link,
                @JsonProperty("start_time") LocalDateTime start_time,
                @JsonProperty("last_updated") LocalDateTime last_updated,
                @JsonProperty("last_accepted") LocalDateTime last_accepted,
                @JsonProperty("required_seconds") int required_seconds) {

            // none of the dates can be later than now...
            LocalDateTime now = LocalDateTime.now();

            if (last_updated != null) {
                // make sure this is the start of the day
                last_updated = last_updated.withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            if (last_updated != null && last_updated.isAfter(now)) {
                last_updated = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            if (last_accepted != null && last_accepted.isAfter(now)) {
                last_accepted = now;
            }

            if (start_time != null && start_time.isAfter(now)) {
                start_time = now;
            }

            this.key = key;
            this.type = type;
            this.link = link;
            this.start_time = start_time;
            this.last_updated = last_updated;
            this.last_accepted = last_accepted;
            this.required_seconds = required_seconds;

            this.assertSane();
        }

        public TandCItem(String key, String type, String link, LocalDateTime last_updated, LocalDateTime last_accepted, int required_seconds) {
            // none of the dates can be later than now...
            LocalDateTime now = LocalDateTime.now();

            if (last_updated != null) {
                // make sure this is the start of the day
                last_updated = last_updated.withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            if (last_updated != null && last_updated.isAfter(now)) {
                last_updated = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            if (last_accepted != null && last_accepted.isAfter(now)) {
                last_accepted = now;
            }

            this.key = key;
            this.type = type;
            this.link = link;
            this.last_updated = last_updated;
            this.last_accepted = last_accepted;
            this.required_seconds = required_seconds;
            this.start_time = null;
        }

        @JsonProperty("key")
        public String getKey() {
            return this.key;
        }

        @JsonProperty("type")
        public String getType() {
            return this.type;
        }

        @JsonProperty("link")
        public String getLink() {
            return this.link;
        }

        @JsonProperty("last_updated")
        public LocalDateTime getLastUpdated() {
            return this.last_updated;
        }

        @JsonProperty("last_accepted")
        public LocalDateTime getLastAccepted() {
            return this.last_accepted;
        }

        @JsonProperty("required_seconds")
        public int getRequiredSeconds() {
            return this.required_seconds;
        }

        @JsonProperty("start_time")
        public LocalDateTime getStartTime() {
            return this.start_time;
        }

        public String toString() {
            // return a JSON-encoded string of this item
            try {
                ObjectMapper mapper = JsonSerialization.mapper;
                // mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true);
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Could not save TandCItem to string: " + e.getMessage());
                return null;
            }
        }

        public AcceptedState acceptedState() {
            if (key == null || type == null || link == null) {
                // no required item
                return AcceptedState.ACCEPTED;
            }

            if (last_accepted == null) {
                // this is required, and the user has never accepted
                return AcceptedState.NOT_ACCEPTED;
            }

            if (last_updated == null) {
                // the user has accepted, and since there is no
                // last updated date, the acceptance must be after
                // the user read the last version
                return AcceptedState.ACCEPTED;
            }

            // otherwise, the user has accepted, and there is a last updated date,
            // so we need to check if the last updated date is after the last acceptance
            if (last_updated.isAfter(last_accepted)) {
                return AcceptedState.NEW_VERSION;
            }
            else {
                return AcceptedState.ACCEPTED;
            }
        }

        public boolean needsAccepting() {
            return acceptedState() != AcceptedState.ACCEPTED;
        }

        public void assertSane() {
            if (key == null || type == null) {
                throw new IllegalArgumentException("key, type, and link must be non-null");
            }

            if (required_seconds < 0) {
                throw new IllegalArgumentException("required_seconds must be non-negative");
            }

            // make sure that this is one of the valid keys...
            if (!key.equals("tandc") && !key.equals("ause") && !key.equals("dpriv")) {
                throw new IllegalArgumentException("key must be one of 'tandc', 'ause', or 'dpriv'");
            }
        }

        public void accept(LocalDateTime accepted_datetime) {
            last_accepted = accepted_datetime;
        }

        public boolean hasStarted() {
            return this.start_time != null;
        }

        public void started() {
            this.start_time = LocalDateTime.now();
        }
    }

    private static class TandCInfo {
        private TandCItem tandc;
        private TandCItem ause;
        private TandCItem dpriv;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public TandCInfo(
                @JsonProperty("tandc") TandCItem tandc,
                @JsonProperty("ause") TandCItem ause,
                @JsonProperty("dpriv") TandCItem dpriv) {

            this.tandc = tandc;
            this.ause = ause;
            this.dpriv = dpriv;
            this.assertSane();
        }

        @JsonProperty("tandc")
        public TandCItem getTandC() {
            return tandc;
        }

        @JsonProperty("ause")
        public TandCItem getAcceptableUse() {
            return ause;
        }

        @JsonProperty("dpriv")
        public TandCItem getDataPrivacy() {
            return dpriv;
        }

        public TandCItem nextToAccept() {
            if (tandc.needsAccepting()) {
                if (!tandc.hasStarted()) {
                    tandc.started();
                }

                return tandc;
            }

            if (ause.needsAccepting()) {
                if (!ause.hasStarted()) {
                    ause.started();
                }

                return ause;
            }

            if (dpriv.needsAccepting()) {
                if (!dpriv.hasStarted()) {
                    dpriv.started();
                }

                return dpriv;
            }

            return null;
        }

        public static TandCInfo load(AuthenticationFlowContext context) {
            UserModel user = context.getUser();
            Map<String,String> config = context.getAuthenticatorConfig().getConfig();

            if (user == null) {
                return null;
            }

            Map<String, List<String>> userAttributes = user.getAttributes();

            List<String> tandc_accepted = userAttributes.get("tandc_accepted");
            List<String> ause_accepted = userAttributes.get("ause_accepted");
            List<String> dpriv_accepted = userAttributes.get("dpriv_accepted");

            // get the latest date of each of these acceptances
            LocalDateTime latest_tandc_accepted = null;

            if (tandc_accepted != null) {
                for (String t : tandc_accepted) {
                    if (t != null) {
                        // read this string into a LocalDateTime - it was written
                        // with the ISO_LOCAL_DATE_TIME format
                        LocalDateTime accepted_datetime = LocalDateTime.parse(t);

                        if (latest_tandc_accepted == null || accepted_datetime.isAfter(latest_tandc_accepted)) {
                            latest_tandc_accepted = accepted_datetime;
                        }
                    }
                }
            }

            LocalDateTime latest_ause_accepted = null;

            if (ause_accepted != null) {
                for (String t : ause_accepted) {
                    if (t != null) {
                        // read this string into a LocalDateTime - it was written
                        // with the ISO_LOCAL_DATE_TIME format
                        LocalDateTime accepted_datetime = LocalDateTime.parse(t);
                        if (latest_ause_accepted == null || accepted_datetime.isAfter(latest_ause_accepted)) {
                            latest_ause_accepted = accepted_datetime;
                        }
                    }
                }
            }

            LocalDateTime latest_dpriv_accepted = null;

            if (dpriv_accepted != null) {
                for (String t : dpriv_accepted) {
                    if (t != null) {
                        // read this string into a LocalDateTime - it was written
                        // with the ISO_LOCAL_DATE_TIME format
                        LocalDateTime accepted_datetime = LocalDateTime.parse(t);
                        if (latest_dpriv_accepted == null || accepted_datetime.isAfter(latest_dpriv_accepted)) {
                            latest_dpriv_accepted = accepted_datetime;
                        }
                    }
                }
            }

            LocalDateTime tand_last_updated = null;

            if (config.get("tandc.last_updated") != null) {
                tand_last_updated = LocalDate.parse(config.get("tandc.last_updated")).atStartOfDay();
            }

            LocalDateTime ause_last_updated = null;

            if (config.get("ause.last_updated") != null) {
                ause_last_updated = LocalDate.parse(config.get("ause.last_updated")).atStartOfDay();
            }

            LocalDateTime dpriv_last_updated = null;

            if (config.get("dpriv.last_updated") != null) {
                dpriv_last_updated = LocalDate.parse(config.get("dpriv.last_updated")).atStartOfDay();
            }

            Integer tandc_required_seconds = 0;

            if (config.get("tandc.required_seconds") != null) {
                tandc_required_seconds = Integer.parseInt(config.get("tandc.required_seconds"));
            }

            Integer ause_required_seconds = 0;

            if (config.get("ause.required_seconds") != null) {
                ause_required_seconds = Integer.parseInt(config.get("ause.required_seconds"));
            }

            Integer dpriv_required_seconds = 0;

            if (config.get("dpriv.required_seconds") != null) {
                dpriv_required_seconds = Integer.parseInt(config.get("dpriv.required_seconds"));
            }

            TandCItem tandc = new TandCItem("tandc", "Access Terms",
                    config.get("tandc.link"),
                    tand_last_updated,
                    latest_tandc_accepted,
                    tandc_required_seconds);

            TandCItem ause = new TandCItem("ause", "Acceptable Use Policy",
                    config.get("ause.link"),
                    ause_last_updated,
                    latest_ause_accepted,
                    ause_required_seconds);

            TandCItem dpriv = new TandCItem("dpriv", "Data Privacy Policy",
                    config.get("dpriv.link"),
                    dpriv_last_updated,
                    latest_dpriv_accepted,
                    dpriv_required_seconds);

            TandCInfo info = new TandCInfo(tandc, ause, dpriv);

            info.assertSane();

            return info;
        }

        public void accept(TandCItem item, LocalDateTime accepted_datetime) {
            if (item.getKey() == null) {
                logger.error("Cannot accept item with null key");
                throw new IllegalArgumentException("Cannot accept item with null key");
            }

            item.accept(accepted_datetime);

            if (tandc != null && item.getKey().contentEquals(tandc.getKey())) {
                tandc = item;
            } else if (ause != null && item.getKey().contentEquals(ause.getKey())) {
                ause = item;
            } else if (dpriv != null && item.getKey().contentEquals(dpriv.getKey())) {
                dpriv = item;
            } else {
                logger.error("Could not update item: " + item.getKey());
                throw new IllegalArgumentException("Could not update item: " + item.getKey());
            }
        }

        public void assertSane() {
            if (tandc != null) {
                tandc.assertSane();
                if (! tandc.getKey().contentEquals("tandc")) {
                    logger.error("tandc key must be 'tandc' " + tandc.getKey() + " " + tandc.toString());
                    throw new IllegalArgumentException("tandc key must be 'tandc'");
                }
            }

            if (ause != null) {
                ause.assertSane();
                if (! ause.getKey().contentEquals("ause")) {
                    logger.error("ause key must be 'ause' " + ause.getKey() + " " + ause.toString());
                    throw new IllegalArgumentException("ause key must be 'ause'");
                }
            }

            if (dpriv != null) {
                dpriv.assertSane();
                if (! dpriv.getKey().contentEquals("dpriv")) {
                    logger.error("dpriv key must be 'dpriv' " + dpriv.getKey() + " " + dpriv.toString());
                    throw new IllegalArgumentException("dpriv key must be 'dpriv'");
                }
            }
        }

        public String toString() {
            // return a JSON-encoded string of this item
            try {
                ObjectMapper mapper = JsonSerialization.mapper;
                // mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true);
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Could not save TandCInfo to string: " + e.getMessage());
                return null;
            }
        }

        public static TandCInfo fromString(String s) {
            // create a TandCInfo from a JSON-encoded string
            try {
                TandCInfo info = JsonSerialization.readValue(s, TandCInfo.class);
                info.assertSane();
                return info;
            } catch (Exception e) {
                logger.error("Could not load TandCInfo from string: " + e.getMessage());
                return null;
            }
        }
    }

    @Override
    public void close() {
    }

    @Transactional
    protected void recordAccepted(UserModel user, String attribute_key, LocalDateTime date_updated) {
        Map<String, List<String>> userAttributes = user.getAttributes();
        List<String> accepted_datetimes = userAttributes.get(attribute_key);

        if (accepted_datetimes == null) {
            accepted_datetimes = new ArrayList<String>();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        String accepted_time = formatter.format(date_updated);

        // make sure we haven't set this already
        for (String t : accepted_datetimes) {
            if (t.contentEquals(accepted_time)) {
                return;
            }
        }

        accepted_datetimes.add(accepted_time);
        user.setAttribute(attribute_key, accepted_datetimes);
    }

    protected Response challenge(AuthenticationFlowContext context,
                                 String error, String field) {
        LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());

        if (error != null) {
            if (field != null) {
                form.addError(new FormMessage(field, error));
            } else {
                form.setError(error);
            }
        }

        AuthenticationSessionModel session = context.getAuthenticationSession();

        // get and unpackage the TandCInfo object
        String tandc_info_string = session.getAuthNote("tandc_info");

        if (tandc_info_string == null) {
            // login failed with internal message
            logger.error("TandCInfo not found in session");
            return Response.serverError().build();
        }

        TandCInfo tandc_info = TandCInfo.fromString(tandc_info_string);

        if (tandc_info == null) {
            // login failed with internal message
            logger.error("TandCInfo could not be loaded from session");
            return Response.serverError().build();
        }

        tandc_info.assertSane();

        // get the next item to accept
        TandCItem next_item = tandc_info.nextToAccept();

        if (next_item == null) {
            // nothing to accept - all done
            return Response.accepted().build();
        }

        String doc_last_updated = null;

        if (next_item.getLastUpdated() != null)
            doc_last_updated = next_item.getLastUpdated().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

        String doc_link = next_item.getLink();
        String doc_type = next_item.getType();
        boolean doc_has_changed = next_item.acceptedState() == AcceptedState.NEW_VERSION;

        // can really accept if it has been more than 5 seconds since the
        // next item was started
        boolean doc_can_really_accept = Duration.between(next_item.getStartTime(), LocalDateTime.now()).getSeconds() > 5;

        Response response = form
                .setAttribute("docLink", doc_link)
                .setAttribute("docType", doc_type)
                .setAttribute("docLastUpdated", doc_last_updated)
                .setAttribute("docHasChanged", doc_has_changed)
                .setAttribute("canReallyAccept", doc_can_really_accept)
                .createForm("tandc_form.ftl");
        context.challenge(response);

        return response;
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();

        // get the tandc_info from the context
        String tandc_info_string = session.getAuthNote("tandc_info");

        if (tandc_info_string == null) {
            logger.error("TandCInfo not found in session");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        TandCInfo tandc_info = TandCInfo.fromString(tandc_info_string);

        if (tandc_info == null) {
            logger.error("TandCInfo could not be loaded from session");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            session.removeAuthNote("tandc_info");
            return;
        }

        tandc_info.assertSane();

        TandCItem next_item = tandc_info.nextToAccept();

        if (next_item == null) {
            // nothing left to accept - all done
            session.removeAuthNote("tandc_info");
            context.success();
            return;
        }

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData != null)
        {
            if (formData.containsKey("cancel")) {
                session.removeAuthNote("tandc_info");
                context.resetFlow();
                return;
            }

            String enteredResponse = formData.getFirst("response");

            if (enteredResponse != null) {
                // lower case and clean up the response
                enteredResponse = enteredResponse.toLowerCase().trim();

                LocalDateTime now = LocalDateTime.now();

                if (enteredResponse.contentEquals("accept")) {
                    // how many seconds since the start time?
                    int accept_seconds = (int) Duration.between(next_item.getStartTime(), now).getSeconds();

                    if (accept_seconds < next_item.getRequiredSeconds()) {
                        // they haven't read it
                        challenge(context, "It has only been " + accept_seconds
                                + " seconds.<br/>Are you sure you have read and understood it fully?", "response");
                        return;
                    }

                    // this has been accepted
                    tandc_info.accept(next_item, now);

                    // record the time of acceptance in the user model as an attribute
                    UserModel user = context.getUser();
                    recordAccepted(user, next_item.getKey() + "_accepted", now);

                    // make sure to evict the user from the cache - this should
                    // (hopefully) force the write
                    KeycloakSession keycloakSession = context.getSession();
                    UserCache cache = keycloakSession.getProvider(UserCache.class);
                    cache.evict(context.getRealm(), user);

                    // re-fetch the user and set again, just to be sure
                    user = keycloakSession.users().getUserById(context.getRealm(), user.getId());
                    recordAccepted(user, next_item.getKey() + "_accepted", now);

                    // get the next item to accept - this will start the timer
                    // for this next item
                    next_item = tandc_info.nextToAccept();

                    // save the updated tandc_info to the session
                    session.setAuthNote("tandc_info", tandc_info.toString());

                    if (next_item != null) {
                        challenge(context, null, null);
                    } else {
                        // we've finished - everything accepted
                        session.removeAuthNote("tandc_info");
                        context.success();
                    }
                    return;
                } else if (enteredResponse.contentEquals("really accept")) {
                    // was this more than 5 seconds since the start time?
                    int accept_seconds = (int) Duration.between(next_item.getStartTime(), now).getSeconds();

                    if (accept_seconds < 5) {
                        challenge(context, "It has only been " + accept_seconds
                                + " seconds.<br/>Are you sure you have read and understood it fully?", "response");
                        return;
                    }

                    // this has been accepted
                    tandc_info.accept(next_item, now);

                    // record the time of acceptance in the user model as an attribute
                    UserModel user = context.getUser();
                    recordAccepted(user, next_item.getKey() + "_accepted", now);

                    // make sure to evict the user from the cache - this should
                    // (hopefully) force the write
                    KeycloakSession keycloakSession = context.getSession();
                    UserCache cache = keycloakSession.getProvider(UserCache.class);
                    cache.evict(context.getRealm(), user);

                    // re-fetch the user and set again, just to be sure
                    user = keycloakSession.users().getUserById(context.getRealm(), user.getId());
                    recordAccepted(user, next_item.getKey() + "_accepted", now);

                    // get the next item to accept - this will start the timer
                    // for this next item
                    next_item = tandc_info.nextToAccept();

                    // save the updated tandc_info to the session
                    session.setAuthNote("tandc_info", tandc_info.toString());

                    if (next_item != null) {
                        challenge(context, null, null);
                    } else {
                        // we've finished - everything accepted
                        session.removeAuthNote("tandc_info");
                        context.success();
                    }
                    return;
                } else {
                    // they didn't accept
                    challenge(context, "You must accept the " + next_item.getType() + " to continue.", "response");
                    return;
                }
            }
        }

        // they didn't accept
        challenge(context, "You must accept the " + next_item.getType() + " to continue.", "response");
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // load up a TandCInfo object that contains all the information
        // about the user's acceptance of T&Cs
        TandCInfo tandc_info = TandCInfo.load(context);

        if (tandc_info == null) {
            logger.error("Could not load TandCInfo from context");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        tandc_info.assertSane();

        // get the next item to accept - this will set the start time
        // for the item if it hasn't already been set
        TandCItem next_item = tandc_info.nextToAccept();

        if (next_item != null) {
            // write this info to the session and then call challenge
            String info_string = tandc_info.toString();

            if (info_string != null) {
                context.getAuthenticationSession().setAuthNote("tandc_info", info_string);
                challenge(context, null, null);
            } else {
                logger.error("Could not save TandCInfo to session");
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            }

            return;
        }

        // nothing to accept - all done
        context.success();
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