<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true displayInfo=false; section>
    <#if section = "header">
        Select your active group
    <#elseif section = "form">
        <form id="isambard-select-group-form" class="isambard-form" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    Choose which group should be active for this application.
                    You can switch it later from within the application.
                </div>

                <#list projects?keys as key>
                    <div class="${properties.kcInputWrapperClass!}">
                        <label>
                            <input type="radio" name="group" value="${key}" <#if key == selected!"">checked</#if> />
                            ${projects[key]} (${key})
                        </label>
                    </div>
                </#list>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="submit" type="submit" value="Select" />
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" type="submit" value="Cancel" />
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
