package javaonedrive;

import java.awt.GridLayout;

import javax.swing.JPanel;
import javax.swing.JTextArea;

public class MessagePanel extends JPanel
{
  private JTextArea text = new JTextArea();

  public MessagePanel()
  {
    text.setEditable(false);
    text.setLineWrap(true); // 自動的に改行する。
    setLayout(new GridLayout(1, 1));
    add(text);
  }

  public void setMessage(String message)
  {
    text.setText(message);
  }
}
