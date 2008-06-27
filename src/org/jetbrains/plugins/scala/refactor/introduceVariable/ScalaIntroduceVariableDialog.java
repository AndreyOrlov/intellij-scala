package org.jetbrains.plugins.scala.refactor.introduceVariable;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.refactoring.HelpID;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.util.HashMap;
import java.util.EventListener;
import java.awt.event.*;

import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesUtil;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableDialogInterface;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaValidator;
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableSettings;
import org.jetbrains.annotations.Nullable;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
public class ScalaIntroduceVariableDialog extends DialogWrapper implements ScalaIntroduceVariableDialogInterface{
  private JPanel contentPane;
  private JCheckBox myCbTypeSpec;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private JLabel myNameLabel;
  private JCheckBox declareVariableCheckBox;
  private JCheckBox myCbReplaceAllOccurences;
  private JLabel myTypeLabel;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private PsiType myType;
  private int occurrencesCount;
  private ScalaValidator validator;
  private String[] possibleNames;

  private HashMap<String, PsiType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.variable.title");


  public ScalaIntroduceVariableDialog(Project project,
                                       PsiType myType,
                                       int occurrencesCount,
                                       ScalaValidator validator,
                                       String[] possibleNames) {
    super(project,true);
    this.project = project;
    this.myType = myType;
    this.occurrencesCount = occurrencesCount;
    this.validator = validator;
    this.possibleNames = possibleNames;
    setUpNameComboBox(possibleNames);

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    updateOkStatus();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

  @Nullable
  public String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String &&
        ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String) myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  public boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurences.isSelected();
  }

  private boolean isDeclareVariable() {
    return declareVariableCheckBox.isSelected();
  }

  private PsiType getSelectedType() {
    if (!myCbTypeSpec.isSelected() || !myCbTypeSpec.isEnabled()) {
      return null;
    } else {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }
  }

  private void setUpDialog() {

    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbReplaceAllOccurences.setFocusable(false);
    declareVariableCheckBox.setMnemonic(KeyEvent.VK_F);
    declareVariableCheckBox.setFocusable(false);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myCbTypeSpec.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    // Type specification
    if (myType == null) {
      myCbTypeSpec.setSelected(false);
      myCbTypeSpec.setEnabled(false);
      myTypeComboBox.setEnabled(false);
    } /*else {
      if (ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY != null) {
        myCbTypeSpec.setSelected(ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY);
        myTypeComboBox.setEnabled(ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY);
      } else {
        myCbTypeSpec.setSelected(true);
        myTypeComboBox.setEnabled(true);
      }
      myTypeMap = GroovyRefactoringUtil.getCompatibleTypeNames(myType);
      for (String typeName : myTypeMap.keySet()) {
        myTypeComboBox.addItem(typeName);
      }
    }*/ //todo: type specification

    if (ScalaApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_VARIABLE != null) {
      declareVariableCheckBox.setSelected(ScalaApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_VARIABLE);
    }

    myCbTypeSpec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myTypeComboBox.setEnabled(myCbTypeSpec.isSelected());
      }
    });

    // Replace occurences
    if (occurrencesCount > 1) {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(true);
      myCbReplaceAllOccurences.setText(myCbReplaceAllOccurences.getText() + " (" + occurrencesCount + " occurrences)");
    } else {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(false);
    }


    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

  }

  private void setUpNameComboBox(String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(project, ScalaFileType.SCALA_FILE_TYPE);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);
    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireNameDataChanged();
          }
        }
    );

    ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    }
    );

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (!validator.isOK(this)) {
      return;
    }
    if (myCbTypeSpec.isEnabled()) {
      ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY = myCbTypeSpec.isSelected();
    }
    if (declareVariableCheckBox.isEnabled()) {
      ScalaApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_VARIABLE = declareVariableCheckBox.isSelected();
    }
    super.doOKAction();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(ScalaNamesUtil.isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  public ScalaIntroduceVariableSettings getSettings() {
    return new MyGroovyIntroduceVariableSettings(this);
  }

  private static class MyGroovyIntroduceVariableSettings implements ScalaIntroduceVariableSettings {
    String myEnteredName;
    boolean myIsReplaceAllOccurrences;
    boolean myIsDeclareFinal;
    PsiType mySelectedType;

    public MyGroovyIntroduceVariableSettings(ScalaIntroduceVariableDialog dialog) {
      myEnteredName = dialog.getEnteredName();
      myIsReplaceAllOccurrences = dialog.isReplaceAllOccurrences();
      myIsDeclareFinal = dialog.isDeclareVariable();
      mySelectedType = dialog.getSelectedType();
    }

    public String getEnteredName() {
      return myEnteredName;
    }

    public boolean isReplaceAllOccurrences() {
      return myIsReplaceAllOccurrences;
    }

    public boolean isDeclareVariable() {
      return myIsDeclareFinal;
    }

    public PsiType getSelectedType() {
      return mySelectedType;
    }

  }
}
