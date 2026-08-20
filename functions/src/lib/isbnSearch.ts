import { createHash } from "node:crypto";
import * as cheerio from "cheerio";

import { DomainError } from "./domain";
import { classifyBookFormat, suggestPhysicalEditionIsbn13 } from "./bookFormat";
import { compactIsbn, normalizeIsbn } from "./isbn";
import type { IsbnBookMetadata } from "../models";

const SOURCE_ORIGIN = "https://isbnsearch.org";
const MAX_RESPONSE_BYTES = 1_000_000;

export interface IsbnSearchResult {
  metadata: IsbnBookMetadata;
  responseHash: string;
}

function parseAuthors(fields: Map<string, string>): string[] {
  const raw = fields.get("authors") ?? fields.get("author");
  if (!raw) {
    return [];
  }
  const names = raw.includes(";") ? raw.split(";") : [raw];
  return names
    .map((name) => name
      .trim()
      .replace(/\s*\([^)]*\)\s*$/, "")
      .replace(/\s+/g, " ")
      .trim())
    .filter((name) => name.length > 0 && name.length <= 100);
}

export function parseIsbnSearchHtml(html: string, requestedIsbn: string): IsbnBookMetadata {
  const requested = normalizeIsbn(requestedIsbn);
  const $ = cheerio.load(html);
  const book = $("#book").first();
  if (!book.length) {
    throw new DomainError("not-found", "ISBNsearch did not return a book.");
  }

  const info = book.find(".bookinfo").first();
  const title = (
    info.find("h1, h2").first().text() ||
    book.find("h1, h2").first().text()
  ).trim();
  if (!title) {
    throw new DomainError("failed-precondition", "ISBNsearch returned malformed book data.");
  }

  const fields = new Map<string, string>();
  info.find("p, li, tr").each((_index, element) => {
    const text = $(element).text().replace(/\s+/g, " ").trim();
    const separator = text.indexOf(":");
    if (separator > 0) {
      fields.set(text.slice(0, separator).trim().toLowerCase(), text.slice(separator + 1).trim());
    }
  });
  const isbn13 = compactIsbn(fields.get("isbn-13") ?? "");
  const isbn10 = compactIsbn(fields.get("isbn-10") ?? "");
  if (!isbn13 || isbn13 !== requested.isbn13) {
    throw new DomainError("failed-precondition", "ISBNsearch returned a different ISBN.");
  }

  const coverUrl = book.find(".image img, .thumbnail img").first().attr("src")?.trim() ?? null;
  const absoluteCoverUrl = coverUrl
    ? new URL(coverUrl, SOURCE_ORIGIN).toString()
    : null;

  const binding = fields.get("binding") ?? null;
  const format = classifyBookFormat(binding);
  const physicalEditionIsbn13 = suggestPhysicalEditionIsbn13(isbn13, format);
  return {
    title,
    isbn13,
    isbn10: isbn10 || requested.isbn10,
    authors: parseAuthors(fields),
    binding,
    format,
    ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
    publisher: fields.get("publisher") ?? null,
    publishedDate: fields.get("published") ?? null,
    coverUrl: absoluteCoverUrl,
    sourceUrl: `${SOURCE_ORIGIN}/isbn/${requested.isbn13}`,
  };
}

export async function fetchIsbnSearch(
  isbn13: string,
  fetchImpl: typeof fetch = fetch,
): Promise<IsbnSearchResult> {
  const sourceUrl = `${SOURCE_ORIGIN}/isbn/${encodeURIComponent(isbn13)}`;
  const response = await fetchImpl(sourceUrl, {
    headers: {
      "accept": "text/html",
      "user-agent": "Maktaba/1.0 (+https://github.com/jmkitavi/maktaba; user-triggered ISBN lookup)",
    },
    redirect: "error",
    signal: AbortSignal.timeout(8_000),
  });

  if (response.status === 404) {
    throw new DomainError("not-found", "No book was found for that ISBN.");
  }
  if (response.status === 429) {
    throw new DomainError("resource-exhausted", "The ISBN provider is temporarily rate-limiting requests.");
  }
  if (!response.ok) {
    throw new DomainError("failed-precondition", "The ISBN provider is temporarily unavailable.");
  }
  const declaredLength = Number(response.headers.get("content-length") ?? 0);
  if (declaredLength > MAX_RESPONSE_BYTES) {
    throw new DomainError("failed-precondition", "The ISBN provider returned an oversized response.");
  }
  const html = await response.text();
  if (Buffer.byteLength(html) > MAX_RESPONSE_BYTES) {
    throw new DomainError("failed-precondition", "The ISBN provider returned an oversized response.");
  }
  return {
    metadata: parseIsbnSearchHtml(html, isbn13),
    responseHash: createHash("sha256").update(html).digest("hex"),
  };
}
