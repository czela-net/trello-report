package net.czela.trello;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

assert "abc" == JsonUtil.escape("abc")
assert "a\\\\bc" == JsonUtil.escape("a\\bc")
assert 'a\\\\bc' == JsonUtil.escape("a\\bc")
assert 'a\\tbc' == JsonUtil.escape("a\tbc")
assert 'a\\rbc' == JsonUtil.escape("a\rbc")
assert 'a\\nbc' == JsonUtil.escape("a\nbc")
assert 'a\\fbc' == JsonUtil.escape("a\fbc")
assert 'a\\bbc' == JsonUtil.escape("a\bbc")
assert 'a\\"bc' == JsonUtil.escape("a\"bc")
