import assert from "node:assert/strict";
import test from "node:test";

import {
  isbn10To13,
  isValidIsbn10,
  isValidIsbn13,
  normalizeIsbn,
} from "../lib/isbn";
import { parseIsbnSearchHtml } from "../lib/isbnSearch";

test("validates and converts ISBN identifiers", () => {
  assert.equal(isValidIsbn10("1250255171"), true);
  assert.equal(isValidIsbn13("9781250255174"), true);
  assert.equal(isbn10To13("1-250-25517-1"), "9781250255174");
  assert.deepEqual(normalizeIsbn("1-250-25517-1"), {
    isbn13: "9781250255174",
    isbn10: "1250255171",
  });
});

test("rejects invalid ISBN checksums", () => {
  assert.equal(isValidIsbn10("1250255172"), false);
  assert.equal(isValidIsbn13("9781250255175"), false);
  assert.throws(() => normalizeIsbn("9781250255175"), /valid ISBN/);
});

test("parses the primary ISBNsearch book section only", () => {
  const html = `
    <div id="book">
      <div class="image"><img src="https://images.isbndb.com/covers/51/74/9781250255174.jpg"></div>
      <div class="bookinfo">
        <h1>Surrounded by Idiots: The Four Types of Human Behavior</h1>
        <p><strong>ISBN-13:</strong> 9781250255174</p>
        <p><strong>ISBN-10:</strong> 1250255171</p>
        <p><strong>Author:</strong> Thomas Erikson</p>
        <p><strong>Binding:</strong> Paperback</p>
        <p><strong>Publisher:</strong> St. Martin's Press</p>
        <p><strong>Published:</strong> 2019-07-30</p>
      </div>
      <div class="offers">$9.99</div>
    </div>`;
  assert.deepEqual(parseIsbnSearchHtml(html, "9781250255174"), {
    title: "Surrounded by Idiots: The Four Types of Human Behavior",
    isbn13: "9781250255174",
    isbn10: "1250255171",
    authors: ["Thomas Erikson"],
    binding: "Paperback",
    format: "PHYSICAL",
    publisher: "St. Martin's Press",
    publishedDate: "2019-07-30",
    coverUrl: "https://images.isbndb.com/covers/51/74/9781250255174.jpg",
    sourceUrl: "https://isbnsearch.org/isbn/9781250255174",
  });
});

test("rejects missing and mismatched book data", () => {
  assert.throws(() => parseIsbnSearchHtml("<main>Not found</main>", "9781250255174"), /did not return/);
  assert.throws(
    () => parseIsbnSearchHtml(
      '<div id="book"><div class="bookinfo"><h1>Wrong</h1><p>ISBN-13: 9780061120084</p></div></div>',
      "9781250255174",
    ),
    /different ISBN/,
  );
});

test("parses plural semicolon-delimited authors", () => {
  const html = `
    <div id="book">
      <div class="bookinfo">
        <h1>The Daily Stoic</h1>
        <p><strong>ISBN-13:</strong> 9781781257654</p>
        <p><strong>ISBN-10:</strong> 1781257655</p>
        <p><strong>Authors:</strong> Ryan Holiday; Stephen Hanselman</p>
      </div>
    </div>`;
  assert.deepEqual(
    parseIsbnSearchHtml(html, "9781781257654").authors,
    ["Ryan Holiday", "Stephen Hanselman"],
  );
});

test("classifies a known ebook and suggests its physical edition", () => {
  const html = `
    <div id="book">
      <div class="bookinfo">
        <h1>The Diary of a CEO</h1>
        <p><strong>ISBN-13:</strong> 9781473591516</p>
        <p><strong>Binding:</strong> E-Book</p>
      </div>
    </div>`;

  const metadata = parseIsbnSearchHtml(html, "9781473591516");
  assert.equal(metadata.binding, "E-Book");
  assert.equal(metadata.format, "DIGITAL");
  assert.equal(metadata.physicalEditionIsbn13, "9781785043666");
});

test("normalizes author roles without splitting comma-formatted names", () => {
  const html = `
    <div id="book">
      <div class="bookinfo">
        <h2>The Daily Stoic</h2>
        <p><strong>ISBN-13:</strong> 9781781257654</p>
        <p><strong>Authors:</strong> Holiday, Ryan (Author); Stephen Hanselman (Editor)</p>
      </div>
    </div>`;
  assert.deepEqual(
    parseIsbnSearchHtml(html, "9781781257654").authors,
    ["Holiday, Ryan", "Stephen Hanselman"],
  );
});
