package fr.minecraftforgefrance.installer;

import argo.jdom.JsonRootNode;
import fr.minecraftforgefrance.common.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;

import static fr.minecraftforgefrance.common.Localization.LANG;

public class InstallerFrame extends JFrame implements IInstallRunner
{
    private static final long serialVersionUID = 1L;
    public File mcDir = EnumOS.getMinecraftDefaultDir();
    public String preSet = null;

    public InstallerFrame()
    {
        this.setTitle(String.format(LANG.getTranslation("title.installer"), RemoteInfoReader.instance().getModPackDisplayName()));
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setResizable(false);

        if(RemoteInfoReader.instance().hasPreset())
        {
            try
            {
                JsonRootNode json = RemoteInfoReader.instance().getPreset();
                this.preSet = json.getStringValue("default");
            }
            catch(Exception e)
            {
                System.err.println("Cannot find default preset");
                e.printStackTrace();
            }
        }

        BufferedImage image = null;
        try
        {
            image = ImageIO.read(this.getClass().getResourceAsStream("/installer/logo.png"));
        }
        catch(Exception ignored)
        {

        }

        final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        if(image != null)
        {
            ImageIcon icon = new ImageIcon(image);
            JLabel logoLabel = new JLabel(icon);
            logoLabel.setAlignmentX(CENTER_ALIGNMENT);
            logoLabel.setAlignmentY(CENTER_ALIGNMENT);
            if(image.getWidth() > dim.width || image.getHeight() + 10 > dim.height)
            {
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.bigimage"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                this.dispose();
            }
            else
            {
                logoLabel.setSize(image.getWidth(), image.getHeight());
                panel.add(logoLabel);
            }
        }

        JPanel buttonPanel = new JPanel();

        JButton install = new JButton(LANG.getTranslation("scr.btn.install"));
        install.addActionListener(e -> {
            InstallerFrame.this.dispose();
            if(!InstallerFrame.this.mcDir.exists() || !InstallerFrame.this.mcDir.isDirectory())
            {
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.mcdirmissing"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            FileChecker checker = new FileChecker(new File(new File(InstallerFrame.this.mcDir, "modpacks"), RemoteInfoReader.instance().getModPackName()));
            ProcessInstall install1 = new ProcessInstall(checker, InstallerFrame.this, InstallerFrame.this.mcDir, InstallerFrame.this.preSet);
            install1.createFrame();
        });
        buttonPanel.add(install);

        if(RemoteInfoReader.instance().hasWebSite())
        {
            JButton webSite = new JButton(LANG.getTranslation("scr.btn.webSite"));
            webSite.addActionListener(e -> {
                try
                {
                    Desktop.getDesktop().browse(new URI(RemoteInfoReader.instance().getWebSite()));
                }
                catch(Exception ex)
                {
                    JOptionPane.showMessageDialog(InstallerFrame.this, String.format(LANG.getTranslation("err.cannotopenurl"), RemoteInfoReader.instance().getWebSite()), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            buttonPanel.add(webSite);
        }

        JButton credit = new JButton(LANG.getTranslation("scr.btn.credits"));
        credit.addActionListener(e -> {
            CreditFrame credit1 = new CreditFrame(InstallerFrame.this);
            credit1.setVisible(true);
        });
        buttonPanel.add(credit);

        JButton option = new JButton(LANG.getTranslation("scr.btn.options"));
        option.addActionListener(e -> {
            OptionFrame credit12 = new OptionFrame(InstallerFrame.this);
            credit12.setVisible(true);
        });
        buttonPanel.add(option);

        JButton cancel = new JButton(LANG.getTranslation("misc.cancel"));
        cancel.addActionListener(e -> InstallerFrame.this.dispose());
        buttonPanel.add(cancel);

        JLabel welcome = new JLabel(RemoteInfoReader.instance().getWelcome());
        welcome.setAlignmentX(CENTER_ALIGNMENT);
        welcome.setAlignmentY(CENTER_ALIGNMENT);

        JLabel mc = new JLabel("Minecraft : " + RemoteInfoReader.instance().getMinecraftVersion());
        mc.setAlignmentX(CENTER_ALIGNMENT);
        mc.setAlignmentY(CENTER_ALIGNMENT);

        JLabel forge = new JLabel("Forge : " + RemoteInfoReader.instance().getForgeVersion());
        forge.setAlignmentX(CENTER_ALIGNMENT);
        forge.setAlignmentY(CENTER_ALIGNMENT);

        panel.add(welcome);
        panel.add(mc);
        panel.add(forge);
        panel.add(buttonPanel);

        this.add(panel);
        addWindowListener(new WindowAdapter()
        {
            public void windowOpened(WindowEvent e)
            {
                requestFocus();
            }
        });
        this.pack();
        this.setLocationRelativeTo(null);
    }

    public void run()
    {
        this.setVisible(true);
    }

    @Override
    public void onFinish()
    {
        SuccessFrame successFrame = new SuccessFrame();
        successFrame.setVisible(true);
    }

    @Override
    public boolean shouldDownloadLib()
    {
        return true;
    }
}