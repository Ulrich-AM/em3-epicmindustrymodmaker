package em3.app;

import em3.ui.EditorUi;
import em3.viewport.SceneRenderer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Desktop entry point. One GLFW window owns the OpenGL context used by Dear ImGui and the scene framebuffer. */
public final class Em3Main {
    public static void main(String[] args) {
        boolean smoke = false;
        boolean withSprites = false;
        int width = 1600;
        int height = 900;
        Path shot = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--smoke-frame".equals(arg)) {
                smoke = true;
            } else if ("--with-sprites".equals(arg)) {
                withSprites = true;
            } else if ("--size".equals(arg) && i + 1 < args.length) {
                String[] parts = args[++i].split("x");
                width = Integer.parseInt(parts[0]);
                height = Integer.parseInt(parts[1]);
            } else if ("--shot".equals(arg) && i + 1 < args.length) {
                shot = Path.of(args[++i]);
            }
        }
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW failed to initialize");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(width, height, "epic mindustry mod maker (em3)", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new IllegalStateException("Could not create the editor window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        if (smoke) {
            System.out.println("GL " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)
                    + " / " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        }

        EditorSession session = new EditorSession();
        Path last = EditorSession.lastProject();
        if (last != null && Files.isDirectory(last)) {
            try {
                session.open(last);
            } catch (Exception e) {
                session.log.error("Could not reopen " + last + ": " + e.getMessage());
            }
        }
        if (withSprites && session.project.sprites.isEmpty()) {
            session.downloadOfficial();
        }
        if (withSprites) {
            session.params.warmup = 1f;
            session.params.recoil = 1f;
            session.params.heat = 0.85f;
            session.playback.driveChannels = false;
        }
        SceneRenderer renderer = new SceneRenderer();
        EditorUi ui = new EditorUi(window, session, renderer);
        ui.init();

        long previous = System.nanoTime();
        int frames = 0;
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - previous) / 1_000_000_000f);
            previous = now;
            session.editorTicks += dt * 60f;
            if (session.test.active) {
                session.test.step(dt, session.params);
                session.playback.driveChannels = false;
            } else {
                session.playback.advance(dt, session.params);
            }
            session.refresh();

            int[] fbW = new int[1];
            int[] fbH = new int[1];
            glfwGetFramebufferSize(window, fbW, fbH);
            glViewport(0, 0, Math.max(1, fbW[0]), Math.max(1, fbH[0]));
            glClearColor(0.08f, 0.08f, 0.09f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            ui.frame();
            glfwSwapBuffers(window);
            frames++;
            if (smoke && frames >= 3) {
                if (shot != null) {
                    try {
                        writeFramebuffer(shot, fbW[0], fbH[0]);
                    } catch (Exception e) {
                        System.err.println("Screenshot failed: " + e.getMessage());
                    }
                }
                glfwSetWindowShouldClose(window, true);
            }
        }
        ui.dispose();
        renderer.forgetTextures();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private static void writeFramebuffer(Path path, int width, int height) throws java.io.IOException {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                int r = buffer.get(i) & 0xff;
                int g = buffer.get(i + 1) & 0xff;
                int b = buffer.get(i + 2) & 0xff;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        ImageIO.write(image, "png", path.toFile());
        int min = 255;
        int max = 0;
        int varied = 0;
        int first = image.getRGB(0, 0) & 0xffffff;
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                int rgb = image.getRGB(x, y) & 0xffffff;
                int lum = ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255);
                min = Math.min(min, lum);
                max = Math.max(max, lum);
                if (rgb != first) {
                    varied++;
                }
            }
        }
        System.out.println("Frame " + w + "x" + h + " lum " + min + ".." + max + " varied samples " + varied);
    }
}
