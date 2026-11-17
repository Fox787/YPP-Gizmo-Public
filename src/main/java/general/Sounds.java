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
 *   werewolves_30_seconds.wav  30 seconds left of stations (also the Cursed Isles forage warning)
 *   plank_swabbie.wav          Cursed Isles: a merc should be planked before the next fray
 *   rumble_warning.wav         rumble: 5 seconds before the punch sizes change
 *
 * Gizmo runs inside the game's Java process, so a sound played here counts as the game's audio,
 * and Discord's "stream with sound" (which captures the streamed app's own audio) sends it to viewers.
 * On Windows the sound is therefore played by a separate, short-lived Windows process (PowerShell's
 * SoundPlayer), which isn't part of the game's audio. Elsewhere, or if that fails, it plays in-process.
 */
public final class Sounds {

	public static final String CLEAR = "werewolves_clear.wav", THIRTY_SECONDS = "werewolves_30_seconds.wav";
	/** From PP_Timer / Thalassa (converted to WAV): the "plank swabbie" voice line and the rumble warning. */
	public static final String PLANK = "plank_swabbie.wav", RUMBLE_WARNING = "rumble_warning.wav";

	private Sounds() { }

	/**
	 * Volume for every Gizmo sound, 0..100 (set from the Options tab). The Windows player has no volume
	 * control of its own, so the samples are scaled and the quieter copy is what gets played.
	 */
	private static volatile int volume = 100;
	public static void setVolume(int v) { volume = Math.max(0, Math.min(100, v)); }
	public static int volume() { return volume; }

	public static void play(String name) {
		int v = volume;
		if (v <= 0) return;
		Thread t = new Thread(() -> {
			if (!playOutside(name, v)) playInside(name, v);
		}, "Gizmo sound");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Plays through a separate process, so it isn't captured with the game's audio: PowerShell's
	 * SoundPlayer on Windows, afplay on macOS, paplay or aplay on Linux. False if none could start.
	 */
	private static boolean playOutside(String name, int vol) {
		String os = System.getProperty("os.name", "").toLowerCase();
		try {
			Path dir = Path.of(System.getProperty("java.io.tmpdir"), "Gizmo-sounds");
			Files.createDirectories(dir);
			// One file per volume, so a change of volume never plays a stale copy.
			Path wav = dir.resolve(vol >= 100 ? name : "vol" + vol + "-" + name);
			if (!Files.exists(wav)) {
				byte[] bytes = wavAt(name, vol);
				if (bytes == null) return false;
				Path tmp = dir.resolve(wav.getFileName() + ".part");
				Files.write(tmp, bytes);
				Files.move(tmp, wav, StandardCopyOption.REPLACE_EXISTING);
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

	private static void playInside(String name, int vol) {
		try {
			byte[] bytes = wavAt(name, vol);
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

	/**
	 * The bundled sound as a WAV file at vol percent. 16-bit PCM samples are scaled; any other format
	 * is returned unchanged (at full volume) rather than not at all.
	 */
	static byte[] wavAt(String name, int vol) {
		byte[] original;
		try (InputStream raw = resource(name)) {
			if (raw == null) return null;
			original = raw.readAllBytes();
		} catch (Exception e) {
			return null;
		}
		if (vol >= 100) return original;
		try {
			AudioInputStream in = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(original));
			javax.sound.sampled.AudioFormat f = in.getFormat();
			if (f.getEncoding() != javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED || f.getSampleSizeInBits() != 16)
				return original;
			byte[] pcm = in.readAllBytes();
			// Perceived loudness is roughly logarithmic: square the slider so its low half isn't all "loud".
			double gain = Math.pow(vol / 100.0, 2);
			boolean big = f.isBigEndian();
			for (int i = 0; i + 1 < pcm.length; i += 2) {
				int hi = big ? i : i + 1, lo = big ? i + 1 : i;
				int sample = (short) ((pcm[hi] << 8) | (pcm[lo] & 0xff));
				int scaled = (int) Math.round(sample * gain);
				pcm[hi] = (byte) (scaled >> 8);
				pcm[lo] = (byte) scaled;
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			AudioSystem.write(new AudioInputStream(new java.io.ByteArrayInputStream(pcm), f, pcm.length / f.getFrameSize()),
					javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
			return out.toByteArray();
		} catch (Exception e) {
			return original;
		}
	}

	/** True if the bundled sound can be decoded (for tests; plays nothing). */
	static boolean decodes(String name) {
		try (InputStream raw = resource(name)) {
			if (raw == null) return false;
			AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
			return in.getFormat() != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * A bundled sound. GizmoBeta's build puts them at /sounds/; the IntelliJ artifact, whose source
	 * root is src/, keeps their folder, so they end up at /main/resources/sounds/.
	 */
	private static InputStream resource(String name) {
		InputStream in = Sounds.class.getResourceAsStream("/sounds/" + name);
		return in != null ? in : Sounds.class.getResourceAsStream("/main/resources/sounds/" + name);
	}
}
