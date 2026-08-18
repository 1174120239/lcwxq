<?php
require_once __DIR__ . '/../source/admin/mutualAidCommon.php';

function assert_same($expected, $actual, $message) {
    if ($expected !== $actual) {
        fwrite(STDERR, $message . ': expected ' . var_export($expected, true)
            . ', got ' . var_export($actual, true) . PHP_EOL);
        exit(1);
    }
}

assert_same('starfree_lost_found_items', mutual_aid_table_name('starfree', 'lost_found_items'), 'table name');
assert_same('starfree_lost_found_items', mutual_aid_table_name('starfree_', 'lost_found_items'), 'table prefix with separator');
assert_same(false, mutual_aid_table_name('starfree`', 'lost_found_items'), 'unsafe prefix');
assert_same('已关闭', mutual_aid_status_label(4), 'closed label');

$settings = array();
$error = '';
assert_same(true, mutual_aid_validate_settings(array(
    'enabled' => '1',
    'minimum_level' => '2',
    'audit_required' => '1',
    'contact_enabled' => '1',
    'daily_contact_limit' => '5',
    'item_expiry_days' => '30',
), $settings, $error), 'valid settings');
assert_same(2, $settings['minimum_level'], 'minimum level');

assert_same(false, mutual_aid_validate_settings(array(
    'minimum_level' => '10',
    'daily_contact_limit' => '5',
    'item_expiry_days' => '30',
), $settings, $error), 'invalid minimum level');

$transition = array();
assert_same(true, mutual_aid_transition('approve', 0, 'ignored', $transition, $error), 'approve pending');
assert_same(1, $transition['next_status'], 'approved status');
assert_same('', $transition['reason'], 'approve clears reason');
assert_same(true, mutual_aid_transition('reopen', 2, '', $transition, $error), 'reopen resolved');
assert_same(false, mutual_aid_transition('reopen', 4, '', $transition, $error), 'closed cannot reopen');
assert_same(false, mutual_aid_transition('reject', 0, '', $transition, $error), 'reject needs reason');
assert_same(true, mutual_aid_transition('close', 3, '', $transition, $error), 'close rejected');

echo "mutualAidCommonTest: OK\n";
