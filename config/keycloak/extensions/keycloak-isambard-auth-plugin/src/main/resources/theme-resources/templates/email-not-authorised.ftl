<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div>
          You are not authorised to connect.
        </div>
        <div>
          You can only connect if you are an authorised member of an
          active project.
        </div>
        <div>
          Please get in touch with the person who invited you onto the
          project and make sure that the project is still active, and that
          they invited you using your correct
          email address (<strong>${email}</strong>).
        </div>
        <div>
            If you believe this is an error, please contact the
            <a href="mailto:${supportEmail}">support team</a>,
            letting them know that you tried to connect using the
            email address <strong>${email}</strong>, and that the
            reason you couldn't connect was because:
        </div>
        <div>
          ${reason}
        </div>
    </#if>
</@layout.registrationLayout>
