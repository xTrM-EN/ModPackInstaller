package fr.minecraftforgefrance.common;

import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.ClientInstall;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static fr.minecraftforgefrance.common.Localization.LANG;

public class ProcessInstallForge implements Consumer<InstallFrame> {

    @Override
    public void accept(InstallFrame frame){
        Install profile = Util.loadInstallProfile();

        frame.setTitle(LANG.getTranslation("title.forgeinstall"));
        frame.fileProgressBar.setIndeterminate(true);

        EventQueue.invokeLater(() -> frame.currentDownload.setText(LANG.getTranslation("title.forgeinstall") + " " + profile.getVersion()));

        ProgressCallback callback = (s, messagePriority) -> Logger.info(s);

        ClientInstall clientInstall = (ClientInstall) Actions.CLIENT.getAction(profile, callback);
        try
        {
            boolean allGood = clientInstall.run(EnumOS.getMinecraftDefaultDir(), s -> true);
            if(!allGood)
            {
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotinstallforge"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            }
            else
            {
                frame.fileProgressBar.setIndeterminate(false);
                frame.fileProgressBar.setValue(100);
            }
        }
        catch (ActionCanceledException e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Forge installation has been cancelled due to whatever the fuck, this isn't supposed to be happening, please report that.", LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
