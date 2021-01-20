package fr.minecraftforgefrance.updater;

import com.google.common.base.Throwables;
import fr.minecraftforgefrance.common.Logger;

import javax.swing.*;
import java.lang.reflect.Method;

import static fr.minecraftforgefrance.common.Localization.LANG;

public class LauncherWrapper {

    private static final LauncherWrapper INSTANCE = new LauncherWrapper();

    private boolean modLauncher;

    LauncherWrapper(){}

    public void supportModLauncher(boolean modLauncher){
        this.modLauncher = modLauncher;
    }

    public void launch(String[] args){
        Logger.info("Launching Minecraft...");

        ClassLoader cl = getClass().getClassLoader();

        Class<?> mainClass;

        try {
            if(modLauncher){
                Logger.info("Enabling ModLauncher support.");
                mainClass = cl.loadClass("cpw.mods.modlauncher.Launcher");
            }else{
                Logger.info("Enabling LaunchWrapper support.");
                mainClass = cl.loadClass("net.minecraft.launchwrapper.Launch");
            }
        } catch(Throwable t){
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.runminecraft"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            throw Throwables.propagate(t);
        }

        try {
            Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch(Throwable t){
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.runminecraft"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            throw Throwables.propagate(t);
        }
    }

    public static LauncherWrapper getInstance(){
        return INSTANCE;
    }

}
