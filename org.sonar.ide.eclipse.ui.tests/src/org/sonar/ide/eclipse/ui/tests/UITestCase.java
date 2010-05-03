package org.sonar.ide.eclipse.ui.tests;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.sonar.ide.eclipse.tests.common.JobHelpers;
import org.sonar.ide.eclipse.tests.common.WorkspaceHelpers;
import org.sonar.ide.test.AbstractSonarIdeTest;

/**
 * TODO use Xvfb ("fake" X-server)
 * 
 * @author Evgeny Mandrikov
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public abstract class UITestCase extends AbstractSonarIdeTest {

  protected static SWTWorkbenchBot bot;

  @BeforeClass
  public final static void beforeClass() throws Exception {
    init(); // TODO Godin: remove
    bot = new SWTWorkbenchBot();
    
    // Clean out projects left over from previous test runs
    clearProjects();

    openPerspective("org.eclipse.jdt.ui.JavaPerspective");
    
    closeView("org.eclipse.ui.views.ContentOutline");
  }
  
  @AfterClass
  public final static void afterClass() throws Exception {
    clearProjects();
    bot.sleep(2000);
  }

  private static void openPerspective(final String id) {
    bot.perspectiveById(id).activate();
  }

  protected static void closeView(final String id) {
    // TODO Godin: what if view doesn't exists
    bot.viewById(id).close();
  }
  
  protected IEditorPart openFile(IProject project, String relPath) throws PartInitException {
    IFile file = project.getFile(relPath);
    // TODO next line should be executed in UI Thread
    return IDE.openEditor(getActivePage(), file, true);
  }
  
  protected static IWorkbenchPage getActivePage() {
    IWorkbench workbench = PlatformUI.getWorkbench();
    return workbench.getWorkbenchWindows()[0].getActivePage();
  }
  
  /**
   * Cleans workspace.
   */
  public static void clearProjects() throws Exception {
    WorkspaceHelpers.cleanWorkspace();
  }

  @After
  public final void finalShot() throws IOException {
    takeScreenShot(getClass().getSimpleName());
  }

  public static File takeScreenShot(String classifier) throws IOException {
    File parent = new File("target/screenshots");
    parent.mkdirs();
    File output = File.createTempFile("swtbot", "-" + classifier + ".png", parent);
    SWTUtils.captureScreenshot(output.getAbsolutePath());
    return output;
  }

  public static Exception takeScreenShot(Throwable e) throws IOException {
    File output = takeScreenShot("exception");
    return new Exception(e.getMessage() + " - " + output, e);
  }

  protected File importMavenProject(String projectName) throws Exception {
    File project = getProject(projectName);
    waitForAllBuildsToComplete();
    bot.menu("File").menu("Import...").click();
    SWTBotShell shell = bot.shell("Import");
    try {
      shell.activate();
      bot.tree().expandNode("Maven").select("Existing Maven Projects");
      bot.button("Next >").click();
      bot.comboBoxWithLabel("Root Directory:").setText(project.getCanonicalPath());
      bot.button("Refresh").click();
      bot.button("Finish").click();
    } finally {
      waitForClose(shell);
    }
    waitForAllBuildsToComplete();
    return project;
  }

  protected void waitForAllBuildsToComplete() {
    waitForAllEditorsToSave();
    JobHelpers.waitForJobsToComplete();
  }

  protected void waitForAllEditorsToSave() {
    // TODO JobHelpers.waitForJobs(EDITOR_JOB_MATCHER, 30 * 1000);
  }

  public static boolean waitForClose(SWTBotShell shell) {
    for (int i = 0; i < 50; i++) {
      if ( !shell.isOpen()) {
        return true;
      }
      bot.sleep(200);
    }
    shell.close();
    return false;
  }
}