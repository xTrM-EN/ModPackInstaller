package fr.minecraftforgefrance.installer;

import fr.minecraftforgefrance.common.Localization;
import fr.minecraftforgefrance.common.RemoteInfoReader;

import javax.swing.*;

public class Installer
{
    public static InstallerFrame frame;

    public static void main(String[] args)
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        Localization.init();

        new RemoteInfoReader(LocalInfoReader.instance().getRemoteUrl());

        if(!RemoteInfoReader.instance().init())
        {
            return;
        }
        frame = new InstallerFrame();
        frame.run();
    }
}