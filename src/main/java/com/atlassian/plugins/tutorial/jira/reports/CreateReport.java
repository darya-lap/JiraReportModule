package com.atlassian.plugins.tutorial.jira.reports;

import com.atlassian.core.util.DateUtils;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import org.apache.log4j.Logger;
import webwork.action.ActionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Scanned
public class CreateReport extends AbstractReport {
    private static final Logger log = Logger.getLogger(CreateReport.class);
    private static final int MAX_HEIGHT = 360;
    private long maxCount = 0;
    private Collection<Long> openIssuesCounts = new ArrayList<>();
    private Collection<String> formattedDates = new ArrayList<>();
    @JiraImport
    private final SearchProvider searchProvider;
    @JiraImport
    private final ProjectManager projectManager;
    private final DateTimeFormatter formatter;
    private final ProjectRoleManager projectRoleManager;

    private Date dueDate;



    public CreateReport(SearchProvider searchProvider, ProjectManager projectManager,
                          @JiraImport DateTimeFormatterFactory dateTimeFormatterFactory) {
        this.searchProvider = searchProvider;
        this.projectManager = projectManager;
        this.projectRoleManager  = ComponentAccessor.getComponent(ProjectRoleManager.class);
        this.formatter = dateTimeFormatterFactory.formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser();
    }


    public String generateReportHtml(ProjectActionSupport action, Map params) throws Exception {


        //fillIssuesCounts(startDate, endDate, interval, action.getLoggedInUser(), projectId);
        List<Number> issueBarHeights = new ArrayList<>();
        if (maxCount > 0) {
            openIssuesCounts.forEach(issueCount ->
                    issueBarHeights.add((issueCount.floatValue() / maxCount) * MAX_HEIGHT)
            );
        }
        Map<String, Object> velocityParams = new HashMap<>();
        velocityParams.put("dueDate", formatter.format(dueDate));

        velocityParams.put("openCount", openIssuesCounts);
        velocityParams.put("issueBarHeights", issueBarHeights);
        velocityParams.put("dates", formattedDates);
        velocityParams.put("maxHeight", MAX_HEIGHT);
        return descriptor.getHtml("view", velocityParams);
    }


    public boolean showReport() {

        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        long projectId;
        Collection<ProjectRole> projectRoles;

        if (ActionContext.getParameters().isEmpty()) {
            String[] url = ActionContext.getRequest().getRequestURL().toString().split("/");
            projectRoles = projectRoleManager.getProjectRoles(user, projectManager.getProjectByCurrentKey(url[url.length-1]));

        } else {
            String[] ids = (String[]) ActionContext.getParameters().get("selectedProjectId");
            projectId = Long.parseLong(ids[0]);
            projectRoles = projectRoleManager.getProjectRoles(user, projectManager.getProjectObj(projectId));
        }

        for (ProjectRole role:projectRoles) {
            if (role.getName().equals("Project-Manager")){
                return true;
            }
        }
        return false;
    }

    private long getOpenIssueCount(ApplicationUser user, Date startDate, Date endDate, Long projectId) throws SearchException {
        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder();
        Query query = queryBuilder.where().createdBetween(startDate, endDate).and().project(projectId).buildQuery();
        return searchProvider.searchCount(query, user);
    }


    private void fillIssuesCounts(Date startDate, Date endDate, Long interval, ApplicationUser user, Long projectId) throws SearchException {
        long intervalValue = interval * DateUtils.DAY_MILLIS;
        Date newStartDate;
        long count;
        while (startDate.before(endDate)) {
            newStartDate = new Date(startDate.getTime() + intervalValue);
            if (newStartDate.after(endDate))
                count = getOpenIssueCount(user, startDate, endDate, projectId);
            else
                count = getOpenIssueCount(user, startDate, newStartDate, projectId);
            if (maxCount < count)
                maxCount = count;
            openIssuesCounts.add(count);
            formattedDates.add(formatter.format(startDate));
            startDate = newStartDate;
        }
    }

    public void validate(ProjectActionSupport action, Map params) {

        try {
            dueDate = formatter.parse(ParameterUtils.getStringParam(params, "dueDate"));
        } catch (IllegalArgumentException e) {
            action.addError("dueDate", action.getText("report.issuecreation.duedate.required"));
            log.error("Exception while parsing dueDate");
        }
    }
}