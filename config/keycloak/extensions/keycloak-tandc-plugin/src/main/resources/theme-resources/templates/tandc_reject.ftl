<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div>
            You are not authorised to connect.
        </div>
        <div>
            You can only connect if you accept the
            <a href="https://docs.isambard.ac.uk/policies/">
              terms and policies
            </a>.
        </div>
    </#if>
</@layout.registrationLayout>
