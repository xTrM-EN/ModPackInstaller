package fr.minecraftforgefrance.common;

import argo.format.JsonFormatter;
import argo.format.PrettyJsonFormatter;
import argo.jdom.*;
import argo.saj.InvalidSyntaxException;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static argo.jdom.JsonNodeBuilders.aStringBuilder;
import static fr.minecraftforgefrance.common.Localization.LANG;

public class ProcessInstallModpack implements Runnable
{
    private final InstallFrame installFrame;
    private final List<LibEntry> missingLibs = new ArrayList<>();

    private static final JsonFormatter JSON_FORMATTER = new PrettyJsonFormatter();

    private final FileChecker fileChecker;
    private final IInstallRunner runner;
    private final String preset;

    public final File mcDir;
    public final File modPackDir;

    private boolean error = false;

    public ProcessInstallModpack(FileChecker file, IInstallRunner runner, File mcDir, String preset)
    {
        this.installFrame = new InstallFrame(this);
        this.fileChecker = file;
        this.runner = runner;
        this.mcDir = mcDir;
        this.modPackDir = new File(new File(mcDir, "modpacks"), RemoteInfoReader.instance().getModPackName());
        this.preset = preset;
    }

    public void createFrame()
    {
        this.installFrame.run();
    }

    @Override
    public void run()
    {
        if(!this.fileChecker.remoteList.isEmpty())
        {
            this.deleteDeprecated();
        }
        else
        {
            this.installFrame.dispose();
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.noFile"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int totalSize = this.getTotalDownloadSize();
        EventQueue.invokeLater(() -> {
            ProcessInstallModpack.this.installFrame.fullProgressBar.setMaximum(totalSize);
            ProcessInstallModpack.this.installFrame.fullProgressBar.setIndeterminate(false);
        });

        this.downloadMod();
        if(this.runner.shouldDownloadLib())
        {
            if(this.runner.shouldInstallForge())
            {
                try
                {
                    this.installForge1_13_plus();
                }
                catch(Throwable t)
                {
                	t.printStackTrace();
                    // bon bah on espere qu'il l'a déja installé
                    JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotinstallforge"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                }
            }

            this.downloadLib();
        }
        if(this.preset != null)
        {
            this.downloadPreset();
        }
        if(!error)
        {
            this.finish();
        }
    }

    public void deleteDeprecated()
    {
        for(FileEntry entry : this.fileChecker.outdatedList)
        {
            File f = new File(this.modPackDir, entry.getPath());
            if(f.delete())
            {
                Logger.info(String.format("%1$s was removed. Its md5 was : %2$s", f.getPath(), entry.getMd5()));
            }
            else
            {
                this.installFrame.dispose();
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotdeletefile") + " : " + f.getPath(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Check for missing libraries
     *
     * @return the sum of all missing libs's size
     */
    private int checkMissingLibs()
    {
        File librariesDir = new File(this.mcDir, "libraries");
        final List<JsonNode> libraries = RemoteInfoReader.instance().getProfileInfo().getArrayNode("libraries");
        EventQueue.invokeLater(() -> {
            ProcessInstallModpack.this.installFrame.fileProgressBar.setMaximum(libraries.size());
            ProcessInstallModpack.this.installFrame.fileProgressBar.setIndeterminate(false);
        });
        int max = 0;
        for(final JsonNode library : libraries)
        {
            ProcessInstallModpack.this.changeCurrentDownloadText(library.getStringValue("name"));
            EventQueue.invokeLater(() -> ProcessInstallModpack.this.installFrame.fileProgressBar.setValue(ProcessInstallModpack.this.installFrame.fileProgressBar.getValue() + 1));

            List<String> checksums = null;
            String libName = library.getStringValue("name");
            if(library.isBooleanValue("required") && library.getBooleanValue("required"))
            {
                if(library.isArrayNode("checksums"))
                {
                    checksums = library.getArrayNode("checksums").stream().map(JsonNode::getText).collect(Collectors.toList());
                }

                Logger.info(String.format("Considering library %s", libName));
                String[] nameparts = Iterables.toArray(Splitter.on(':').split(libName), String.class);
                nameparts[0] = nameparts[0].replace('.', '/');
                String jarName = nameparts[1] + '-' + nameparts[2] + ".jar";
                String pathName = nameparts[0] + '/' + nameparts[1] + '/' + nameparts[2] + '/' + jarName;
                File libPath = new File(librariesDir, pathName.replace('/', File.separatorChar));
                String libURL = DownloadUtils.LIBRARIES_URL;
                if(library.isStringValue("url"))
                {
                    libURL = library.getStringValue("url") + (!library.getStringValue("url").endsWith("/") ? "/" : "");
                }
                if(libPath.exists() && DownloadUtils.checksumValid(libPath, checksums))
                {
                    continue;
                }

                libPath.getParentFile().mkdirs();
                libURL += pathName;
                File pack = null;
                boolean xz = false;
                if(library.isBooleanValue("xz") && library.getBooleanValue("xz"))
                {
                    xz = true;
                    pack = new File(libPath.getParentFile(), libPath.getName() + DownloadUtils.PACK_NAME);
                    libURL += DownloadUtils.PACK_NAME;
                }
                if(library.isStringValue("directURL"))
                {
                    libURL = library.getStringValue("directURL");
                }
                try
                {
                    URL url = new URL(libURL);
                    URLConnection connection = url.openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:10.0) Gecko/20100101 Firefox/55.0");
                    int fileLength = connection.getContentLength();
                    max += fileLength;
                    this.missingLibs.add(new LibEntry(libURL, libName, libPath, pack, fileLength, xz));
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return max;
    }

    /**
     * Get the sum of all files's size to download
     *
     * @return the size
     */
    private int getTotalDownloadSize()
    {
        int size = 0;
        for(FileEntry entry : this.fileChecker.missingList)
        {
            size += entry.getSize();
        }
        if(this.runner.shouldDownloadLib())
        {
            size += this.checkMissingLibs();
        }
        return size;
    }

    public void downloadMod()
    {
        this.installFrame.setTitle(LANG.getTranslation("proc.downloadingmods"));

        for(FileEntry entry : this.fileChecker.missingList)
        {
            File f = new File(this.modPackDir, entry.getPath());
            if(f.getParentFile() != null && !f.getParentFile().isDirectory())
            {
                f.getParentFile().mkdirs();
            }
            this.changeCurrentDownloadText(entry.getPath());
            Logger.info(String.format("Downloading file %1$s to %2$s (its md5 is %3$s)", entry.getUrl().toString(), f.getPath(), entry.getMd5()));
            if(!DownloadUtils.downloadFile(entry.getUrl(), f, this.installFrame))
            {
                this.installFrame.dispose();
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotdownload") + " : " + entry.getUrl().toString(), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                Thread.currentThread().interrupt();
                this.error = true;
                return;
            }
        }
    }

    /**
     * TODO: make a less hacky approach to that
     *
     * @throws IOException
     *      if something goes wrong
     * @throws ReflectiveOperationException
     *      if something goes wrong
     */
    public void installForge1_13_plus() throws IOException, ReflectiveOperationException
    {
        this.installFrame.setTitle(LANG.getTranslation("title.forge"));
        String version = RemoteInfoReader.instance().getMinecraftVersion() + "-" + RemoteInfoReader.instance().getForgeVersion();
        this.changeCurrentDownloadText(String.format(LANG.getTranslation("proc.downloadingforge"), version));

        URL installerURL = new URL("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + version + "/forge-" + version + "-installer.jar");
        File temp = File.createTempFile("forge-" + version + "-installer", ".jar");
        temp.deleteOnExit();

        if(!DownloadUtils.downloadFile(installerURL, temp, this.installFrame))
        {
            this.installFrame.dispose();
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotdownload") + " : " + installerURL.toString(), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            Thread.currentThread().interrupt();
            this.error = true;
            return;
        }

        URLClassLoader classLoader = (URLClassLoader) getClass().getClassLoader();
        Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addUrlMethod.setAccessible(true);
        addUrlMethod.invoke(classLoader, temp.toURI().toURL());

        Consumer<InstallFrame> consumer = (Consumer<InstallFrame>) classLoader.loadClass("fr.minecraftforgefrance.common.ProcessInstallForge").getConstructor().newInstance();
        consumer.accept(installFrame);
    }

    public void downloadLib()
    {
        this.installFrame.setTitle(LANG.getTranslation("title.libs"));

        for(LibEntry entry : this.missingLibs)
        {
            this.changeCurrentDownloadText(String.format(LANG.getTranslation("proc.downloadinglib"), entry.getName()));
            try
            {
                File filePath = entry.isXZ() ? entry.getPackDest() : entry.getDest();
                if(!DownloadUtils.downloadFile(new URL(entry.getUrl()), filePath, this.installFrame))
                {
                    this.installFrame.dispose();
                    JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotdownload") + " : " + entry.getUrl() + (entry.isXZ() ? DownloadUtils.PACK_NAME : ""), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                    Thread.currentThread().interrupt();
                    this.error = true;
                    return;
                }
                else if(entry.isXZ())
                {
                    try
                    {
                        this.changeCurrentDownloadText(LANG.getTranslation("proc.unpackingfile") + " : " + entry.getPackDest().toString());
                        DownloadUtils.unpackLibrary(entry.getDest(), Files.toByteArray(entry.getPackDest()));
                        this.changeCurrentDownloadText(String.format(LANG.getTranslation("file.unpacked.success"), entry.getPackDest().toString()));
                        entry.getPackDest().delete();
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            catch(HeadlessException | MalformedURLException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void downloadPreset()
    {
        this.installFrame.setTitle(LANG.getTranslation("title.preset"));
        final JsonRootNode data = RemoteInfoReader.instance().getPreset();
        final JsonNodeSelector<JsonNode, List<JsonNode>> preSet = JsonNodeSelectors.anArrayNode(this.preset);
        final JsonNodeSelector<JsonNode, String> preSetName = JsonNodeSelectors.aStringNode();
        List<String> files = new AbstractList<String>()
        {
            public String get(int index)
            {
                return preSetName.getValue(preSet.getValue(data).get(index));
            }

            public int size()
            {
                return preSet.getValue(data).size();
            }
        };
        for(String file : files)
        {
            File destFile = new File(this.modPackDir, file);
            if(!destFile.exists())
            {
                if(!destFile.getParentFile().exists())
                {
                    destFile.getParentFile().mkdirs();
                }
                this.changeCurrentDownloadText(file);
                try
                {
                    if(!DownloadUtils.downloadFile(new URL(RemoteInfoReader.instance().getPresetUrl() + this.preset + "/" + DownloadUtils.escapeURIPathParam(file)), destFile, this.installFrame))
                    {
                        this.installFrame.dispose();
                        JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotdownload") + " : " + RemoteInfoReader.instance().getPresetUrl() + this.preset + "/" + DownloadUtils.escapeURIPathParam(file), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
                        Thread.currentThread().interrupt();
                        this.error = true;
                        return;
                    }
                }
                catch(HeadlessException | MalformedURLException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private void changeCurrentDownloadText(final String text)
    {
        EventQueue.invokeLater(() -> ProcessInstallModpack.this.installFrame.currentDownload.setText(text));
    }

    public void finish()
    {
        EventQueue.invokeLater(() -> {
            ProcessInstallModpack.this.installFrame.fullProgressBar.setMaximum(100);
            ProcessInstallModpack.this.installFrame.fullProgressBar.setValue(100);
        });
        this.installFrame.setTitle(LANG.getTranslation("misc.finishing"));
        this.createOrUpdateProfile();
        this.writeModPackInfo();
        this.addToProfileList();
        this.installFrame.dispose();
        this.runner.onFinish();
    }

    /**
     * create or update the file modpackName.json in the folder .minecraft/profile/modpackName
     */
    private void createOrUpdateProfile()
    {
        String modpackName = RemoteInfoReader.instance().getModPackName();
        File versionRootDir = new File(this.mcDir, "versions");
        File modpackVersionDir = new File(versionRootDir, modpackName);
        if(!modpackVersionDir.exists())
        {
            modpackVersionDir.mkdirs();
        }
        File modpackJson = new File(modpackVersionDir, modpackName + ".json");

        JsonRootNode versionJson = JsonNodeFactories.object(RemoteInfoReader.instance().getProfileInfo().getFields());
        try
        {
            BufferedWriter newWriter = Files.newWriter(modpackJson, Charsets.UTF_8);
            JSON_FORMATTER.format(versionJson, newWriter);
            newWriter.close();
        }
        catch(Exception e)
        {
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotwriteversion"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * determine if the profile exist and if it is valid in the file launcher_profiles.json
     *
     * @param profiles
     *            JsonRootNode of the file launcher_profiles.json
     * @param modpackName
     *            name of the modpack
     * @param displayName
     *            display name of the modpack
     * @return true if the profile exist and is valid
     */
    private boolean isProfileValid(JsonRootNode profiles, String modpackName, String displayName)
    {
        if(profiles.isObjectNode("profiles", displayName))
        {
            if(profiles.isStringValue("profiles", displayName, "name") && profiles.getStringValue("profiles", displayName, "name").equals(displayName))
            {
                return profiles.getStringValue("profiles", displayName, "lastVersionId").equals(modpackName);
            }
        }
        return false;
    }

    /**
     * add the profile in the file launcher_profiles.json
     */
    private void addToProfileList()
    {
        String modpackName = RemoteInfoReader.instance().getModPackName();
        String displayName = RemoteInfoReader.instance().getModPackDisplayName();
        File launcherProfiles = new File(this.mcDir, "launcher_profiles.json");
        JdomParser parser = new JdomParser();
        JsonRootNode jsonProfileData;
        if(!launcherProfiles.exists())
        {
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.mcprofilemissing"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            this.installFrame.dispose();
            return;
        }
        try
        {
            jsonProfileData = parser.parse(Files.newReader(launcherProfiles, Charsets.UTF_8));
        }
        catch(InvalidSyntaxException e)
        {
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.mcprofilecorrupted"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            throw Throwables.propagate(e);
        }
        catch(Exception e)
        {
            throw Throwables.propagate(e);
        }

        if(!isProfileValid(jsonProfileData, modpackName, displayName))
        {
            JsonField[] fields = new JsonField[RemoteInfoReader.instance().hasArgument() ? 5 : 4];
            fields[0] = JsonNodeFactories.field("name", JsonNodeFactories.string(displayName));
            fields[1] = JsonNodeFactories.field("lastVersionId", JsonNodeFactories.string(modpackName));
            fields[2] = JsonNodeFactories.field("type", JsonNodeFactories.string("custom"));
            fields[3] = JsonNodeFactories.field("gameDir", JsonNodeFactories.string(this.modPackDir.getAbsoluteFile().toString()));
            if(RemoteInfoReader.instance().hasArgument())
            {
                fields[4] = JsonNodeFactories.field("javaArgs", JsonNodeFactories.string(RemoteInfoReader.instance().getArgument()));
            }

            HashMap<JsonStringNode, JsonNode> profileCopy = Maps.newHashMap(jsonProfileData.getNode("profiles").getFields());
            HashMap<JsonStringNode, JsonNode> rootCopy = Maps.newHashMap(jsonProfileData.getFields());
            profileCopy.put(JsonNodeFactories.string(displayName), JsonNodeFactories.object(fields));
            JsonRootNode profileJsonCopy = JsonNodeFactories.object(profileCopy);

            rootCopy.put(JsonNodeFactories.string("profiles"), profileJsonCopy);

            jsonProfileData = JsonNodeFactories.object(rootCopy);

            try
            {
                BufferedWriter newWriter = Files.newWriter(launcherProfiles, Charsets.UTF_8);
                JSON_FORMATTER.format(jsonProfileData, newWriter);
                newWriter.close();
            }
            catch(Exception e)
            {
                JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotwriteprofile"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void writeModPackInfo()
    {
        File info = new File(this.modPackDir, RemoteInfoReader.instance().getModPackName() + ".json");
        if(!info.exists())
        {
            try
            {
                info.createNewFile();
            }
            catch(IOException e)
            {
                throw Throwables.propagate(e);
            }
        }

        JsonObjectNodeBuilder jsonBuilder = JsonNodeBuilders.anObjectBuilder().withField("forge", aStringBuilder(RemoteInfoReader.instance().getForgeVersion()));
        jsonBuilder.withField("remote", aStringBuilder(RemoteInfoReader.instance().remoteUrl));
        if(RemoteInfoReader.instance().hasChangeLog())
        {
            JsonRootNode changeLog = RemoteInfoReader.instance().getChangeLog();
            if(changeLog != null && changeLog.hasFields())
                jsonBuilder.withField("currentVersion", aStringBuilder(changeLog.getFieldList().get(0).getName().getText()));
        }
        JsonRootNode json = jsonBuilder.build();
        try
        {
            BufferedWriter writer = Files.newWriter(info, Charsets.UTF_8);
            JSON_FORMATTER.format(json, writer);
            writer.close();
        }
        catch(Exception e)
        {
            JOptionPane.showMessageDialog(null, LANG.getTranslation("err.cannotwriteversion"), LANG.getTranslation("misc.error"), JOptionPane.ERROR_MESSAGE);
        }
    }
}