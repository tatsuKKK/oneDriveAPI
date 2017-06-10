package javaonedrive;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class MetaDataPanel extends JPanel
{
  private JTree tree = new JTree();
  private JButton button = new JButton("戻る");

  public MetaDataPanel(JavaOneDrive frame)
  {
    button.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        frame.showFolderPanel(); // 戻るボタンがおされたら、フォルダ内のアイテムのリストを再表示。
      }
    });

    setLayout(new BorderLayout());
    add(new JScrollPane(tree), BorderLayout.CENTER);
    add(button, BorderLayout.SOUTH);
  }

  public void setObject(Object object)
  {
    DefaultMutableTreeNode node = buildMetaDataTreeNodes(null, object);
    DefaultTreeModel model = new DefaultTreeModel(node);
    tree.setModel(model);
  }

  private DefaultMutableTreeNode buildMetaDataTreeNodes(String name, Object object)
  {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();

    if(name!=null && object==null)
    {
      node.setUserObject(name + " = null");
      return node;
    }

    String class_name = object.getClass().getName();

    if(class_name.indexOf("onedrive.json")==0)
    {
      if(name!=null)
      {
        node.setUserObject(name + " - " + object.getClass().getSimpleName());
      }
      else
      {
        node.setUserObject(object.getClass().getSimpleName());
      }

      Field[] fields = object.getClass().getDeclaredFields();

      for(Field f : fields)
      {
        f.setAccessible(true);

        try
        {
          Object child = f.get(object);
          String name_child = f.getName();

          if(name_child!=null && name_child.compareTo("serialVersionUID")!=0)
          {
            DefaultMutableTreeNode node_child = buildMetaDataTreeNodes(name_child, child); // 再起呼び出し。
            node.add(node_child);
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }
    else if(object instanceof List)
    {
      if(name!=null)
      {
        node.setUserObject(name);
      }
      else
      {
        node.setUserObject(object.getClass().getSimpleName());
      }

      for(Object child : (List)object)
      {
        DefaultMutableTreeNode node_child = buildMetaDataTreeNodes(null, child); // 再起呼び出し。
        node.add(node_child);
      }
    }
    else if(name!=null)
    {
      node.setUserObject(name + " = " + object);
    }
    else
    {
      node.setUserObject(object);
    }

    return node;
  }
}