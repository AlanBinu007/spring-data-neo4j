/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.core.transaction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apiguardian.api.API;
import org.neo4j.driver.Bookmark;
import org.springframework.lang.Nullable;

/**
 * Responsible for storing, updating and retrieving the bookmarks of Neo4j's transaction.
 *
 * @author Michael J. Simons
 * @soundtrack Metallica - Death Magnetic
 * @since 6.0
 */
@API(status = API.Status.STABLE, since = "6.1.1")
public final class Neo4jBookmarkManager {

	public static Neo4jBookmarkManager create() {
		return new Neo4jBookmarkManager(null, null);
	}

	public static Neo4jBookmarkManager create(@Nullable Supplier<Set<Bookmark>> bookmarksSupplier,
			@Nullable Consumer<Set<Bookmark>> updatedBookmarksConsumer) {
		return new Neo4jBookmarkManager(bookmarksSupplier, updatedBookmarksConsumer);
	}

	private final Set<Bookmark> bookmarks = new HashSet<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final Lock read = lock.readLock();
	private final Lock write = lock.writeLock();

	private final Supplier<Set<Bookmark>> bookmarksSupplier;

	@Nullable
	private final Consumer<Set<Bookmark>> updatedBookmarksConsumer;

	private Neo4jBookmarkManager(@Nullable Supplier<Set<Bookmark>> bookmarksSupplier, @Nullable Consumer<Set<Bookmark>> updatedBookmarksConsumer) {
		this.updatedBookmarksConsumer = updatedBookmarksConsumer;
		this.bookmarksSupplier = bookmarksSupplier == null ? () -> Collections.emptySet() : bookmarksSupplier;
	}

	Collection<Bookmark> getBookmarks() {

		try {
			read.lock();
			HashSet<Bookmark> bookmarksToUse = new HashSet<>(this.bookmarks);
			bookmarksToUse.addAll(bookmarksSupplier.get());
			return Collections.unmodifiableSet(bookmarksToUse);
		} finally {
			read.unlock();
		}
	}

	void updateBookmarks(Collection<Bookmark> usedBookmarks, Bookmark lastBookmark) {
		try {
			write.lock();
			bookmarks.removeAll(usedBookmarks);
			bookmarks.add(lastBookmark);
			if (updatedBookmarksConsumer != null) {
				updatedBookmarksConsumer.accept(Collections.unmodifiableSet(new HashSet<>(bookmarks)));
			}
		} finally {
			write.unlock();
		}
	}
}
