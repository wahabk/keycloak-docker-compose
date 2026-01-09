<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div>
            You are not authorised to connect.
        </div>
        <div>
            If you believe this is an error, please contact the
            <a href="mailto:${supportEmail}">support team</a>,
            letting them know that you tried to connect using the
            email address <strong>${email}</strong>.
        </div>
    </#if>
</@layout.registrationLayout>
