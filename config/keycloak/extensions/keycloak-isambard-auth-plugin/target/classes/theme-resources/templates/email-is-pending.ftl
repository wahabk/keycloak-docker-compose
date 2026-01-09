<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <div>
            Your invitation from ${inviter} is under review. We expect
            to review your invitation quickly (within 72 hours) and will
            notify you by email when your account is ready.
        </div>
        <div>
            If you have any questions, or more than 2 working days have,
            passed, then please ask ${inviter} to submit a ticket
            to the <a href="https://support.isambard.ac.uk">service desk</a>
            requesting that your invitation is reviewed.
        </div>
        <div>
            Note that invitations should normally be sent to official
            (work) email addresses. Invitations sent to personal or
            social email addresses are likely to be rejected.
        </div>
    </#if>
</@layout.registrationLayout>
