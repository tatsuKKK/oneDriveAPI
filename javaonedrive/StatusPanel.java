package javaonedrive;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class StatusPanel extends JPanel
{
  private JTextField text = new JTextField();

  public StatusPanel()
  {
    text.setEditable(false);
    text.setHorizontalAlignment(JTextField.CENTER);
    text.setAlignmentY(0.5f);
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    this.add(text);
  }

  public void setStatus(String status)
  {
    text.setText(status);
  }
}