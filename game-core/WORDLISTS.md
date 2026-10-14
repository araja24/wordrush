# Word lists

Files: `src/main/resources/dev/wordrush/core/words/`

| File | Contents | Source |
|---|---|---|
| `answers.txt` | 2,300 answers: the most frequent words in SGB that are also in ENABLE, excluding most plurals (`-s` but not `-ss/-us/-is`), past tenses (`-ed` but not `-eed`), and a short blocklist of slurs/offensive words | SGB + ENABLE |
| `allowed.txt` | 8,903 accepted guesses: union of ENABLE 5-letter words, SGB, and the answers | SGB + ENABLE |

- **SGB**: `sgb-words.txt` from Donald Knuth's *Stanford GraphBase* (5,757 common five-letter words, ordered by frequency). https://www-cs-faculty.stanford.edu/~knuth/sgb.html
- **ENABLE**: *Enhanced North American Benchmark Lexicon* (`enable1.txt`), public domain.

These are deliberately not the New York Times Wordle lists.
