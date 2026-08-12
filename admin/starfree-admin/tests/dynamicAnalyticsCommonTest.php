<?php
require_once __DIR__ . '/../source/admin/dynamicAnalyticsCommon.php';

function assert_same($expected, $actual, $message) {
    if ($expected !== $actual) {
        fwrite(STDERR, $message . ': expected ' . var_export($expected, true)
            . ', got ' . var_export($actual, true) . PHP_EOL);
        exit(1);
    }
}

assert_same(null, da_percent(0, 0), 'empty denominator');
assert_same(50.0, da_percent(1, 2), 'half percentage');
assert_same('50.0%', da_percent_text(50), 'percentage label');
assert_same('--', da_percent_text(null), 'empty percentage label');
assert_same('1,234', da_number(1234), 'number formatting');
assert_same('--', da_time(0), 'empty timestamp');
assert_same('测试中文...', da_excerpt('测试中文内容', 4), 'UTF-8 excerpt');
assert_same('COALESCE(a.uid,s.uid)', da_actual_uid_sql('s', 'a'), 'anonymous owner SQL');

echo "dynamic analytics helpers: ok\n";
