package fr.minecraftforgefrance.updater;

public class ModLauncherWrapper {

    private static final ModLauncherWrapper INSTANCE = new ModLauncherWrapper();

    private ModLauncherWrapper(){

    }

    public static ModLauncherWrapper getInstance(){
        return INSTANCE;
    }

}
