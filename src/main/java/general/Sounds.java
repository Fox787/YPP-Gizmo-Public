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
 * Gizmo runs inside the game's Java process, so a sound played here counts as the game's audio, and
 * Discord's "stream with sound" sends it to viewers. Discord captures the game's whole process tree, so
 * on Windows the sound is played by a player Windows starts (see separatePlayer), outside that tree.
 * If that can't start: a PowerShell the game starts (in the tree), then in-process. With Debug on, the
 * Output tab says which route each sound took.
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

	/** Where a sound went, for Debug output: "name: route". */
	private static volatile java.util.function.Consumer<String> reporter = message -> { };
	public static void setReporter(java.util.function.Consumer<String> r) { reporter = r == null ? message -> { } : r; }
	private static void report(String name, String route) { reporter.accept(name + ": " + route); }

	public static void play(String name) {
		if (volume <= 0) return;
		Thread t = new Thread(() -> {
			Path wav = scaledFile(name);
			if (wav == null) { report(name, "not found in Gizmo.jar, nothing played"); return; }
			boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
			if (windows && playSeparately(wav)) {
				report(name, "separate player (pid " + playerPid + ", started by Windows, outside the game's processes)");
			} else if (playChild(wav, windows)) {
				report(name, windows ? "PowerShell started by the game (Discord's stream audio can include it)"
						: "system player started by the game");
			} else {
				playInside(name);
				report(name, "inside the game's process (Discord's stream audio includes it)");
			}
		}, "Gizmo sound");
		t.setDaemon(true);
		t.start();
	}

	/** Starts the separate player ahead of the first sound, so that one isn't late. Windows only. */
	public static void prepare() {
		if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) return;
		Thread t = new Thread(Sounds::separatePlayer, "Gizmo sound player start");
		t.setDaemon(true);
		t.start();
	}

	/** The bundled sound at the current volume as a WAV file in the temp folder (cached), or null. */
	private static Path scaledFile(String name) {
		try {
			Path dir = Path.of(System.getProperty("java.io.tmpdir"), "Gizmo-sounds");
			Files.createDirectories(dir);
			int vol = volume;
			Path wav = dir.resolve(name.replace(".wav", "-" + vol + ".wav"));
			if (!Files.exists(wav)) {
				byte[] bytes = scaled(name, vol);
				if (bytes == null) return null;
				Path tmp = Files.createTempFile(dir, "sound-", ".wav");
				try {
					Files.write(tmp, bytes);
					Files.move(tmp, wav, StandardCopyOption.REPLACE_EXISTING);
				} finally {
					Files.deleteIfExists(tmp);
				}
			}
			return wav.toAbsolutePath();
		} catch (Exception e) {
			return null;
		}
	}

	/*
	 * Discord's "stream with sound" captures the streamed app's whole process tree, so a player the
	 * game starts (even a separate PowerShell) is still captured. On Windows, Gizmo therefore asks
	 * Windows' WMI service to start the player: its parent is WmiPrvSE.exe, outside the game's tree.
	 * That player runs hidden for as long as the game does, and plays each WAV whose path Gizmo drops
	 * in its folder as a .play file (picked up within about 0.1 s).
	 */
	private static final Object PLAYER = new Object();
	private static volatile long playerPid;
	private static volatile Path playerDir;
	private static final java.util.concurrent.atomic.AtomicLong requestNumber = new java.util.concurrent.atomic.AtomicLong();
	/** How long the player may take to pick up a request before Gizmo falls back to another route. */
	static final long PICKUP_MS = 1_500;

	private static boolean playSeparately(Path wav) {
		if (separatePlayer() == 0) return false;
		try {
			Path dir = playerDir;
			String id = String.format("%019d-%06d", System.currentTimeMillis(), requestNumber.incrementAndGet() % 1_000_000);
			Path tmp = dir.resolve(id + ".tmp"), request = dir.resolve(id + ".play");
			// UTF-8 with a byte order mark, so Windows PowerShell reads any path correctly.
			Files.write(tmp, ("﻿" + wav).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			Files.move(tmp, request, StandardCopyOption.ATOMIC_MOVE);
			long until = System.currentTimeMillis() + PICKUP_MS;
			while (Files.exists(request) && System.currentTimeMillis() < until) Thread.sleep(20);
			if (!Files.exists(request)) return true;
			// Not picked up: take the request back (so it can't play late) and start a new player next time.
			if (Files.deleteIfExists(request)) {
				synchronized (PLAYER) { playerPid = 0; }
				return false;
			}
			return true;   // picked up just now
		} catch (Exception e) {
			return false;
		}
	}

	/** The separate player's pid, starting it if it isn't running; 0 if it can't be started. */
	static long separatePlayer() {
		synchronized (PLAYER) {
			long pid = playerPid;
			if (pid != 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) return pid;
			playerPid = 0;
			try {
				long owner = ProcessHandle.current().pid();
				Path dir = Path.of(System.getProperty("java.io.tmpdir"), "Gizmo-sounds", "player-" + owner);
				Files.createDirectories(dir);
				try (var old = Files.list(dir)) {
					for (Path f : (Iterable<Path>) old::iterator) Files.deleteIfExists(f);
				}
				String body = String.join("\n",
						"$dir = '" + dir.toString().replace("'", "''") + "'",
						"$owner = " + owner,
						"$player = New-Object System.Media.SoundPlayer",
						"$n = 0",
						"while ($true) {",
						"  $n++",
						"  if ($n % 20 -eq 0 -and -not (Get-Process -Id $owner -ErrorAction SilentlyContinue)) { break }",
						"  $req = Get-ChildItem -LiteralPath $dir -Filter '*.play' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1",
						"  if ($req) {",
						"    $wav = Get-Content -LiteralPath $req.FullName -Raw -ErrorAction SilentlyContinue",
						"    Remove-Item -LiteralPath $req.FullName -ErrorAction SilentlyContinue",
						"    if ($wav) { try { $player.SoundLocation = $wav.Trim(); $player.PlaySync() } catch { } }",
						"    continue",
						"  }",
						"  Start-Sleep -Milliseconds 100",
						"}",
						"Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue");
				String launcher = String.join("\n",
						"$ProgressPreference = 'SilentlyContinue'",
						"$si = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{ShowWindow = [uint16]0}",
						"$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{",
						"  CommandLine = 'powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -EncodedCommand " + encoded(body) + "'",
						"  ProcessStartupInformation = $si }",
						"\"$($r.ReturnValue) $($r.ProcessId)\"");
				Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
						"-EncodedCommand", encoded(launcher)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
				String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
				p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
				// "<return value> <pid>": 0 is success.
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*0\\s+(\\d+)\\s*$").matcher(out);
				if (m.find()) {
					playerDir = dir;
					playerPid = Long.parseLong(m.group(1));
				}
			} catch (Exception e) {
				playerPid = 0;
			}
			return playerPid;
		}
	}

	/** PowerShell's -EncodedCommand form: the script as UTF-16LE, base64. */
	private static String encoded(String script) {
		return java.util.Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
	}

	/**
	 * The earlier route: a player the game starts itself (PowerShell's SoundPlayer on Windows, afplay on
	 * macOS, paplay or aplay on Linux). Outside the game's own process, but in its process tree.
	 */
	private static boolean playChild(Path wav, boolean windows) {
		String os = System.getProperty("os.name", "").toLowerCase();
		String path = wav.toString();
		java.util.List<String[]> commands = new java.util.ArrayList<>();
		if (windows) {
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
