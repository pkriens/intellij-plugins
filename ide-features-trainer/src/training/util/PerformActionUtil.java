package training.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;

/**
 * Created by karashevich on 02/02/15.
 */
public class PerformActionUtil {

    /**
     * Some util method for <i>performAction</i> method
     *
     * @param actionName - please see it in <i>performAction</i> method
     * @return
     */
    public static InputEvent getInputEvent(String actionName) {
        final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
        KeyStroke keyStroke = null;
        for (Shortcut each : shortcuts) {
            if (each instanceof KeyboardShortcut) {
                keyStroke = ((KeyboardShortcut) each).getFirstKeyStroke();
                if (keyStroke != null) break;
            }
        }

        if (keyStroke != null) {
            return new KeyEvent(JOptionPane.getRootFrame(),
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    keyStroke.getModifiers(),
                    keyStroke.getKeyCode(),
                    keyStroke.getKeyChar(),
                    KeyEvent.KEY_LOCATION_STANDARD);
        } else {
            return new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
        }
    }

    /**
     * performing internal platform action
     *
     * @param actionName - name of IntelliJ Action. For full list please see http://git.jetbrains.org/?p=idea/community.git;a=blob;f=platform/platform-api/src/com/intellij/openapi/actionSystem/IdeActions.java;hb=HEAD
     */
    public static void performAction(final String actionName, final Editor editor, final Project project, final Runnable runnable) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true).doWhenDone(runnable);
                    }
                });
            }
        });
    }

    /**
     * performing internal platform action
     *
     * @param actionName - name of IntelliJ Action. For full list please see http://git.jetbrains.org/?p=idea/community.git;a=blob;f=platform/platform-api/src/com/intellij/openapi/actionSystem/IdeActions.java;hb=HEAD
     */
    public static void performAction(final String actionName, final Editor editor, final Project project) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true);
                    }
                });
            }
        });
    }


    /**
     * performing internal platform action
     *
     * @param actionName - name of IntelliJ Action. For full list please see http://git.jetbrains.org/?p=idea/community.git;a=blob;f=platform/platform-api/src/com/intellij/openapi/actionSystem/IdeActions.java;hb=HEAD
     */
    public static void performAction(final String actionName, final Editor editor, final AnActionEvent e, final Runnable runnable) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(e.getProject(), new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true).doWhenDone(runnable);
                    }
                });
            }
        });
    }

    public static void performActionDisabledPresentation(String actionName, final Editor editor){

        final ActionManagerEx amEx = ActionManagerEx.getInstanceEx();
        final AnAction action = amEx.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        final Presentation presentation = action.getTemplatePresentation().clone();
        final DataManager dataManager = DataManager.getInstance();
        Component contextComponent = editor.getContentComponent();
        final DataContext context = (contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext());



        AnActionEvent event = new AnActionEvent(
                inputEvent, context,
                ActionPlaces.UNKNOWN,
                presentation, amEx,
                inputEvent.getModifiersEx()
        );
        amEx.fireBeforeActionPerformed(action, context, event);

        ActionUtil.performActionDumbAware(action, event);
        amEx.queueActionPerformedEvent(action, context, event);
    }

    public static void performEditorAction(String actionId, Editor editor){
        final ActionManagerEx amEx = ActionManagerEx.getInstanceEx();
        final AnAction action = amEx.getAction(actionId);
        final InputEvent inputEvent = getInputEvent(actionId);

        final Presentation presentation = action.getTemplatePresentation().clone();
        final DataManager dataManager = DataManager.getInstance();
        Component contextComponent = editor.getContentComponent();
        final DataContext context = (contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext());



        AnActionEvent event = new AnActionEvent(
                inputEvent, context,
                ActionPlaces.UNKNOWN,
                presentation, amEx,
                inputEvent.getModifiersEx()
        );
        amEx.fireBeforeActionPerformed(action, context, event);

        ActionUtil.performActionDumbAware(action, event);
        amEx.queueActionPerformedEvent(action, context, event);

    }

    public static void sleepHere(final Editor editor, final int delay) throws InterruptedException {

        Thread sleepThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (editor) {
                    try {
                        Thread.sleep(delay);
                        editor.notify();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        sleepThread.start();
        synchronized(editor){
            editor.wait();
        }
        sleepThread.join();


//        alarm.addRequest(new Runnable() {
//            @Override
//            public void run() {
//                synchronized (editor) {
//                    System.err.println("run");
//                    editor.notifyAll();
//                }
//            }
//        }, delay);
//
//        synchronized(editor) {
//                try {
//                    System.err.println("waited");
//                    editor.wait();
//                    System.err.println("stop waited");
//                } catch (InterruptedException e) {
//            }
//        }
    }
}