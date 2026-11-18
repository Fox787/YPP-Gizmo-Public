package main.java.general;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Plays Thalassa's Woodhallow alert sounds, bundled in the jar under /sounds:
 *   werewolves_clear.wav       the last werewolf of the wave has been turned back
 *   werewolves_30_seconds.wav  30 seconds left of stations
 *
 * Gizmo runs inside the game's Java process, so a sound played here counts as the game's audio,
 * and Discord's "stream with sound" (which captures the streamed app's own audio) sends it to viewers.
 * On Windows the sound is therefore played by a separate, short-lived Windows process (PowerShell's
 * SoundPlayer), which isn't part of the game's audio. Elsewhere, or if that fails, it plays in-process.
 *
 * None of this goes through the game's sound settings. Gizmo's own volume (0-100) scales the
 * samples before playing, so it works the same with every player and on every OS.
 */
public final class Sounds {

	public static final String CLEAR = "werewolves_clear.wav", THIRTY_SECONDS = "werewolves_30_seconds.wav";
	/** From PP_Timer / Thalassa (converted to WAV): the "plank swabbie" voice line and the rumble warning. */
	public static final String PLANK = "plank_swabbie.wav", RUMBLE_WARNING = "rumble_warning.wav";

	private Sounds() { }

	/** Gizmo's sound volume, 0-100 (100 = the files as recorded). */
	private static volatile int volume = 100;
	public static void setVolume(int v) { volume = Math.max(0, Math.min(100, v)); }
	public static int volume() { return volume; }

	/** The bundled sound with its samples scaled to the volume (16- or 8-bit PCM), as a WAV file's bytes. */
	static byte[] scaled(String name, int vol) throws Exception {
		try (InputStream raw = resource(name)) {
			if (raw == null) return null;
			AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
			javax.sound.sampled.AudioFormat f = in.getFormat();
			byte[] pcm = in.readAllBytes();
			double k = vol / 100.0;
			boolean signed = f.getEncoding() == javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
			if (k < 1 && f.getSampleSizeInBits() == 16 && signed) {
				boolean big = f.isBigEndian();
				for (int i = 0; i + 1 < pcm.length; i += 2) {
					int lo = pcm[big ? i + 1 : i] & 0xff, hi = pcm[big ? i : i + 1];
					int v = (int) Math.round(((hi << 8) | lo) * k);
					pcm[big ? i + 1 : i] = (byte) v;
					pcm[big ? i : i + 1] = (byte) (v >> 8);
				}
			} else if (k < 1 && f.getSampleSizeInBits() == 8) {
				for (int i = 0; i < pcm.length; i++)
					pcm[i] = signed ? (byte) Math.round(pcm[i] * k) : (byte) (128 + Math.round(((pcm[i] & 0xff) - 128) * k));
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			AudioSystem.write(new AudioInputStream(new java.io.ByteArrayInputStream(pcm), f, pcm.length / f.getFrameSize()),
					javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
			return out.toByteArray();
		}
	}

	/**
	 * A bundled sound. A build that copies resources puts them at /sounds/; the IntelliJ artifact, whose
	 * source root is src/, keeps their folder, so in Gizmo.jar they are at /main/resources/sounds/.
	 */
	static InputStream resource(String name) {
		InputStream in = Sounds.class.getResourceAsStream("/sounds/" + name);
		return in != null ? in : Sounds.class.getResourceAsStream("/main/resources/sounds/" + name);
	}

	public static void play(String name) {
		if (volume <= 0) return;
		Thread t = new Thread(() -> {
			if (!playOutside(name)) playInside(name);
		}, "Gizmo sound");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Plays through a separate process, so it isn't captured with the game's audio: PowerShell's
	 * SoundPlayer on Windows, afplay on macOS, paplay or aplay on Linux. False if none could start.
	 */
	private static boolean playOutside(String name) {
		String os = System.getProperty("os.name", "").toLowerCase();
		try {
			Path dir = Path.of(System.getProperty("java.io.tmpdir"), "Gizmo-sounds");
			Files.createDirectories(dir);
			int vol = volume;
			Path wav = dir.resolve(name.replace(".wav", "-" + vol + ".wav"));
			if (!Files.exists(wav)) {
				byte[] bytes = scaled(name, vol);
				if (bytes == null) return false;
				Path tmp = Files.createTempFile(dir, "sound-", ".wav");
				try {
					Files.write(tmp, bytes);
					Files.move(tmp, wav, StandardCopyOption.REPLACE_EXISTING);
				} finally {
					Files.deleteIfExists(tmp);
				}
			}
			String path = wav.toAbsolutePath().toString();
			java.util.List<String[]> commands = new java.util.ArrayList<>();
			if (os.startsWith("windows")) {
				commands.add(new String[]{"powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
						"-Command", "(New-Object System.Media.SoundPlayer '" + path.replace("'", "''") + "').PlaySync()"});
			} else if (os.contains("mac")) {
				commands.add(new String[]{"afplay", path});
			} else {
				commands.add(new String[]{"paplay", path});
				commands.add(new String[]{"aplay", "-q", path});
			}
			for (String[] cmd : commands) {
				try {
					Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
							.redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
					if (p.isAlive() || p.exitValue() == 0) return true;
				} catch (java.io.IOException notInstalled) {
					// Try the next player.
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	private static void playInside(String name) {
		try {
			byte[] bytes = scaled(name, volume);
			if (bytes == null) return;
			AudioInputStream in = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(bytes));
			Clip clip = AudioSystem.getClip();
			clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP) clip.close(); });
			clip.open(in);
			clip.start();
		} catch (Exception ignored) {
			// No sound device, unsupported format: nothing to do.
		}
	}

}
