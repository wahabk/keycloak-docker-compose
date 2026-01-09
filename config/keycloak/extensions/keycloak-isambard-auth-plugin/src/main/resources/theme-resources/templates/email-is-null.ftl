<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div>
            There is no email address associated with your account. This
            could either be because your identity provider (IdP) does not
            share your email address, or because your account is missing
            a validated email address.
        </div>
        <div>
            If you believe this is an error, please contact the
            <a href="mailto:${supportEmail}">support team</a>.
        </div>
    </#if>
</@layout.registrationLayout>
