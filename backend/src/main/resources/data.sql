-- Seed data: the 10 topics, their prerequisite edges, and 2 demo users. Nothing else.
-- ON CONFLICT DO NOTHING makes this safe to re-run on every startup against persistent Postgres.

-- Topic ids ARE the fixed learning order and the HLR one-hot index (id - 1). Never renumber.
INSERT INTO topics (id, name, slug) VALUES
    (1,  'Arrays',         'arrays'),
    (2,  'Strings',        'strings'),
    (3,  'Two Pointers',   'two-pointers'),
    (4,  'Sliding Window', 'sliding-window'),
    (5,  'HashMap',        'hashmap'),
    (6,  'LinkedList',     'linkedlist'),
    (7,  'Stack',          'stack'),
    (8,  'Trees',          'trees'),
    (9,  'Graphs',         'graphs'),
    (10, 'DP',             'dp')
ON CONFLICT DO NOTHING;

-- (topic_id, prerequisite_id): "to study topic_id, first be solid on prerequisite_id".
-- Approved graph; see docs/CONTRACT.md section 2. Arrays -> LinkedList is deliberately absent.
INSERT INTO topic_prerequisites (topic_id, prerequisite_id) VALUES
    (3,  1),   -- Arrays       -> Two Pointers
    (4,  3),   -- Two Pointers -> Sliding Window
    (4,  2),   -- Strings      -> Sliding Window
    (5,  1),   -- Arrays       -> HashMap
    (5,  2),   -- Strings      -> HashMap
    (7,  1),   -- Arrays       -> Stack
    (7,  2),   -- Strings      -> Stack
    (8,  6),   -- LinkedList   -> Trees
    (8,  7),   -- Stack        -> Trees
    (9,  8),   -- Trees        -> Graphs
    (9,  5),   -- HashMap      -> Graphs
    (10, 5),   -- HashMap      -> DP
    (10, 8)    -- Trees        -> DP
ON CONFLICT DO NOTHING;

-- Demo users for the user switcher (no real login). History for Alex is added in a later phase.
INSERT INTO users (display_name) VALUES
    ('Alex'),
    ('Sam')
ON CONFLICT DO NOTHING;
