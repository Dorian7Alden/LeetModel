UPDATE `problem`
SET `content_markdown` = REPLACE(
    `content_markdown`,
    'The following data files are provided (in CSV or GeoJSON format):',
    'No downloadable data files are attached to this practice problem. The filenames below describe the recommended input schema only; prepare or simulate suitable public data:'
)
WHERE `id` = 51004
  AND `content_markdown` LIKE '%The following data files are provided (in CSV or GeoJSON format):%';

UPDATE `problem`
SET `content_markdown` = REPLACE(
    `content_markdown`,
    'The following data files are provided (CSV format):',
    'No downloadable data files are attached to this practice problem. The filenames below describe the recommended input schema only; prepare or simulate suitable public data:'
)
WHERE `id` = 51005
  AND `content_markdown` LIKE '%The following data files are provided (CSV format):%';
