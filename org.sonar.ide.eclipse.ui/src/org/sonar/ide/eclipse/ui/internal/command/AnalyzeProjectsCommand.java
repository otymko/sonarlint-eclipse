/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2013 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.ui.internal.command;

import com.google.common.collect.Lists;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.sonar.ide.eclipse.core.internal.jobs.AnalyseProjectRequest;
import org.sonar.ide.eclipse.core.internal.jobs.SynchronizeAllIssuesJob;
import org.sonar.ide.eclipse.ui.internal.SonarUiPlugin;
import org.sonar.ide.eclipse.ui.internal.console.SonarConsole;
import org.sonar.ide.eclipse.ui.internal.views.issues.IssuesView;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeProjectsCommand extends AbstractHandler {

  public Object execute(ExecutionEvent event) throws ExecutionException {
    List<IProject> selectedProjects = Lists.newArrayList();

    IEditorPart activeEditor = HandlerUtil.getActiveEditor(event);
    if (activeEditor != null) {
      findProjectOfSelectedEditor(selectedProjects, activeEditor);
    } else {
      findSelectedProjects(event, selectedProjects);
    }

    runAnalysisJob(selectedProjects);

    return null;
  }

  private void runAnalysisJob(List<IProject> selectedProjects) {
    boolean debugEnabled = SonarConsole.isDebugEnabled();
    String sonarJvmArgs = SonarUiPlugin.getSonarJvmArgs();
    List<AnalyseProjectRequest> requests = new ArrayList<AnalyseProjectRequest>();
    SonarUiPlugin.getDefault().getSonarConsole().clearConsole();
    for (IProject project : selectedProjects) {
      requests.add(new AnalyseProjectRequest(project)
        .setDebugEnabled(debugEnabled)
        .setExtraProps(SonarUiPlugin.getExtraPropertiesForLocalAnalysis(project))
        .setJvmArgs(sonarJvmArgs));
    }
    SynchronizeAllIssuesJob job = new SynchronizeAllIssuesJob(requests);
    showIssuesViewAfterJobSuccess(job);
  }

  private void findSelectedProjects(ExecutionEvent event, List<IProject> selectedProjects) throws ExecutionException {
    ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);

    if (selection instanceof IStructuredSelection) {
      @SuppressWarnings("rawtypes")
      List elems = ((IStructuredSelection) selection).toList();
      for (Object elem : elems) {
        if (elem instanceof IProject) {
          selectedProjects.add((IProject) elem);
        }
        else if (elem instanceof IAdaptable) {
          IProject proj = (IProject) ((IAdaptable) elem).getAdapter(IProject.class);
          if (proj != null) {
            selectedProjects.add(proj);
          }
        }
      }
    }
  }

  private void findProjectOfSelectedEditor(List<IProject> selectedProjects, IEditorPart activeEditor) {
    IEditorInput input = activeEditor.getEditorInput();
    if (input instanceof IFileEditorInput) {
      IFile currentFile = ((IFileEditorInput) input).getFile();
      selectedProjects.add(currentFile.getProject());
    }
  }

  protected void showIssuesViewAfterJobSuccess(Job job) {
    // Display issues view after analysis is completed
    job.addJobChangeListener(new JobChangeAdapter() {
      @Override
      public void done(IJobChangeEvent event) {
        if (Status.OK_STATUS == event.getResult()) {
          Display.getDefault().asyncExec(new Runnable() {
            public void run() {
              IWorkbenchWindow iw = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
              try {
                iw.getActivePage().showView(IssuesView.ID);
              } catch (PartInitException e) {
                SonarUiPlugin.getDefault().getLog().log(new Status(Status.ERROR, SonarUiPlugin.PLUGIN_ID, Status.OK, "Unable to open Issues View", e));
              }
            }
          });
        }
      }
    });
    job.schedule();
  }

}
