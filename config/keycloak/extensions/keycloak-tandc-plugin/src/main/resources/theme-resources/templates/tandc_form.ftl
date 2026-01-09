<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section="header">
        ${docType}
    <#elseif section="form">
        <form id="isambard-tandc-form" class="isambard-form" action="${url.loginAction}"
            method="post">

            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    Please <a href="${docLink}" target="_blank" rel="noopener noreferrer">read the ${docType}</a>.
                <div>
                <#if docLastUpdated??>
                    <div class="${properties.kcLabelWrapperClass!}">
                        These were last updated on the ${docLastUpdated}.
                    </div>
                </#if>
                <#if docHasChanged!false>
                    <div class="${properties.kcLabelWrapperClass!}">
                        <span class="pf-v5-c-helper-text__item-text">The ${docType} have changed since you last accepted them</span>
                    </div>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <#if docHasChanged!false>
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="response" class="${properties.kcLabelClass!}">Type "accept" below to confirm you accept the updated ${docType}</label>
                    </div>
                    <#if canReallyAccept>
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="response" class="${properties.kcLabelClass!}">Type "really accept" below to confirm now that you absolutely have read and will accept the updated ${docType}</label>
                        </div>
                    </#if>
                <#else>
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="response" class="${properties.kcLabelClass!}">Type "accept" below to confirm you accept the ${docType}</label>
                    </div>
                    <#if canReallyAccept>
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="response" class="${properties.kcLabelClass!}">Type "really accept" below to confirm now that you absolutely have read and will accept the ${docType}</label>
                        </div>
                    </#if>
                </#if>

                <div class="${properties.kcLabelWrapperClass!}">
                  Note that the date and time that you accept the ${docType} will be recorded,
                  with this record used, if needed, to demonstrate your acceptance
                  if there is a dispute.
                </div>

                <div class="${properties.kcInputWrapperClass!}">
                    <input id="response" name="response" autocomplete="off" type="text" class="isambard-form-input">
                    <#if messagesPerField.existsError('response')>
                        <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}"
                              aria-live="polite">
                          Please type "accept" to confirm you accept the ${docType}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="submit" type="submit" value="Submit" />
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" type="submit" value="Cancel"/>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>