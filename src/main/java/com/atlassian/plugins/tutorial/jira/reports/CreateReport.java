package com.atlassian.plugins.tutorial.jira.reports;

import com.atlassian.annotations.JIRA;
import com.atlassian.core.util.DateUtils;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.project.Project;
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
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserProjectHistoryManager;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.jira.web.action.util.sharing.SharedEntitySearchViewHelper;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.oauth.event.AccessTokenRemovedEvent;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.joda.time.DateTime;
import webwork.action.ActionContext;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Scanned
public class CreateReport extends AbstractReport {
    private static final Logger log = Logger.getLogger(CreateReport.class);

    private Collection<Issue> allIssues;
    @JiraImport
    private final ProjectManager projectManager;
    private final DateTimeFormatter formatter;
    private final ProjectRoleManager projectRoleManager;
    private final SearchService searchService;
    private final SimpleDateFormat dateFormat;

    private ApplicationUser user;
    private long projectId;

    private Date dueDate;


    public CreateReport(ProjectManager projectManager,
                        final SearchService searchService,
                          @JiraImport DateTimeFormatterFactory dateTimeFormatterFactory) {
        this.projectManager = projectManager;
        this.projectRoleManager  = ComponentAccessor.getComponent(ProjectRoleManager.class);
        this.formatter = dateTimeFormatterFactory.formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser();
        this.user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        this.searchService = searchService;
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    }


    public String generateReportHtml(ProjectActionSupport action, Map params) throws Exception {

        getAllIssues(dueDate);
        Map<String, Object> velocityParams = new HashMap<>();
        velocityParams.put("allIssues", this.allIssues);
        velocityParams.put("projectName", Objects.requireNonNull(projectManager.getProjectObj(projectId)).getName());
        velocityParams.put("dueDate", this.dueDate);
        velocityParams.put("dateFormat", dateFormat);
        return descriptor.getHtml("view", velocityParams);
    }


    public boolean showReport() {

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

    private void getAllIssues(Date dueDate) throws SearchException {
        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder();
        Query query = queryBuilder.where().due().lt(dueDate).and().project(this.projectId).buildQuery();
        SearchResults results = searchService.search(user, query, PagerFilter.getUnlimitedFilter());
        allIssues =  results.getIssues();
    }



    public void validate(ProjectActionSupport action, Map params) {
        if (ParameterUtils.getStringParam(params, "dueDate").isEmpty()){
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime midnight = now.toLocalDate().atStartOfDay();
            dueDate = Date.from(midnight.atZone(ZoneId.systemDefault()).toInstant());
        }
        else{

            try {
                dueDate = formatter.parse(ParameterUtils.getStringParam(params, "dueDate"));

            } catch (IllegalArgumentException e) {
                action.addError("dueDate", action.getText("report.issuecreation.duedate.required"));
                log.error("Exception while parsing dueDate");
            }
        }
    }
}