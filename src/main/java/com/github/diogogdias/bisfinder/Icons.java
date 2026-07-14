package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pictures for the panel.
 *
 * <p>Prayer and potion icons ship with the plugin — there are only a few dozen and they are tiny.
 * Monster pictures do not: the wiki calculator has 2,442 of them totalling 181MB, so they are fetched
 * one at a time from its CDN and cached on disk.
 *
 * <p>Equipment is not here at all: RuneLite's own ItemManager already has every item sprite.
 */
@Slf4j
@Singleton
public class Icons
{
	private static final HttpUrl MONSTERS =
		HttpUrl.get("https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/monsters/");
	private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "bis-finder/images");

	/** Roughly the height of a line of text in the panel. */
	private static final int ICON_SIZE = 20;
	private static final int MONSTER_SIZE = 64;
	private static final int THUMBNAIL_SIZE = 24;

	private final OkHttpClient httpClient;
	// Concurrent, and never locked across a network call: the list renderer reads these from the UI thread
	// while a background thread is fetching. A synchronized map would freeze the UI for the length of a
	// download, which is exactly what it used to do.
	private final Map<String, BufferedImage> monsters = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> thumbnails = new ConcurrentHashMap<>();

	/** Images the CDN has none of, so they are not asked for again. */
	private final Set<String> missing = ConcurrentHashMap.newKeySet();

	@Inject
	public Icons(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	public BufferedImage prayer(Prayer prayer)
	{
		// The wiki calculator's files are named after the prayer, with spaces as underscores.
		return resource("/prayers/" + prayer.getPrayerName().replace(' ', '_') + ".png", ICON_SIZE);
	}

	public BufferedImage potion(Potion potion)
	{
		if (potion == Potion.NONE)
		{
			return null;
		}
		return resource("/potions/" + potion.getIconFile() + ".png", ICON_SIZE);
	}

	/**
	 * Blocks on a network call the first time a monster is seen, so it must not be called on the client
	 * thread or the EDT.
	 *
	 * @param image the monster's image file name, straight from the wiki data
	 */
	public BufferedImage monster(String image)
	{
		if (image == null || image.isEmpty())
		{
			return null;
		}

		BufferedImage cached = monsters.get(image);
		if (cached != null || missing.contains(image))
		{
			return cached;
		}

		BufferedImage loaded = scale(readMonster(image), MONSTER_SIZE);
		if (loaded == null)
		{
			missing.add(image);
			return null;
		}

		monsters.put(image, loaded);
		return loaded;
	}

	/** The list-sized picture, only if it is already held. Never touches the network. */
	public BufferedImage cachedThumbnail(String image)
	{
		return image == null ? null : thumbnails.get(image);
	}

	/**
	 * Blocks on a network call the first time a monster is seen, so it must not be called on the client
	 * thread or the EDT.
	 */
	public BufferedImage thumbnail(String image)
	{
		if (image == null || image.isEmpty())
		{
			return null;
		}

		BufferedImage cached = thumbnails.get(image);
		if (cached != null || missing.contains(image))
		{
			return cached;
		}

		BufferedImage loaded = scale(readMonster(image), THUMBNAIL_SIZE);
		if (loaded == null)
		{
			missing.add(image);
			return null;
		}

		thumbnails.put(image, loaded);
		return loaded;
	}

	private BufferedImage readMonster(String image)
	{
		File cached = new File(CACHE_DIR, image);
		if (cached.isFile())
		{
			try
			{
				return ImageIO.read(cached);
			}
			catch (IOException e)
			{
				log.debug("Discarding unreadable cached image {}", cached, e);
			}
		}

		HttpUrl url = MONSTERS.newBuilder().addPathSegment(image).build();
		Request request = new Request.Builder().url(url).build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				log.debug("No picture for {} ({})", image, response.code());
				return null;
			}

			byte[] bytes = body.bytes();
			BufferedImage picture = ImageIO.read(new java.io.ByteArrayInputStream(bytes));

			if (picture != null)
			{
				cache(cached, bytes);
			}
			return picture;
		}
		catch (IOException e)
		{
			log.debug("Could not fetch picture for {}", image, e);
			return null;
		}
	}

	private void cache(File file, byte[] bytes)
	{
		try
		{
			java.nio.file.Files.createDirectories(CACHE_DIR.toPath());
			java.nio.file.Files.write(file.toPath(), bytes);
		}
		catch (IOException e)
		{
			log.debug("Could not cache {}", file, e);
		}
	}

	private BufferedImage resource(String path, int size)
	{
		try (InputStream in = Icons.class.getResourceAsStream(path))
		{
			if (in == null)
			{
				log.debug("Missing bundled icon {}", path);
				return null;
			}
			return scale(ImageIO.read(in), size);
		}
		catch (IOException e)
		{
			log.debug("Could not read bundled icon {}", path, e);
			return null;
		}
	}

	/** Fits the picture inside a square of the given size, keeping its shape. */
	private static BufferedImage scale(BufferedImage source, int size)
	{
		if (source == null)
		{
			return null;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		if (width <= size && height <= size)
		{
			return source;
		}

		double factor = Math.min((double) size / width, (double) size / height);
		int scaledWidth = Math.max(1, (int) Math.round(width * factor));
		int scaledHeight = Math.max(1, (int) Math.round(height * factor));

		BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = scaled.createGraphics();
		g.drawImage(source.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH), 0, 0, null);
		g.dispose();
		return scaled;
	}
}
