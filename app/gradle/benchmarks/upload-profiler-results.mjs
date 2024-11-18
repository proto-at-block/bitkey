/**
 * TODO: This script is in JavaScript, 
 *   but for long term maintainability, 
 *   we probably want to rewrite it in either Python, or Kotlin.
 * 
 *   W-5537/redo-benchmark-upload-script-in-kotlin-or-python
 */ 
import fs from 'fs';
import path from 'path';
import summary from './util/summary.mjs';
import asyncRequest from './util/asyncRequest.mjs';

const testName = process.argv[2];
const branch = process.argv[3];
const commit = process.argv[4];
const benchmarkFile = process.argv[5];
const tags = process.argv.slice(6);

console.log(`Processing results for test '${testName}' branch '${branch}' commit '${commit}' from '${benchmarkFile}'`);
console.log(`Additional tags ${tags}`)

const datadogApiKey = process.env.DATADOG_API_KEY;
const datadogEnvironment = process.env.DATADOG_ENV || 'local';

// We round down at the nearest hour so that all tests
// make their reports at roughly the same time
const now = Math.floor(Date.now() / 1000);
const roundedNow = now - (now % 3600);

const benchmarkLines = fs.readFileSync(path.join(benchmarkFile), 'utf8').split(/\r?\n/);
const names = benchmarkLines.pop().split(',').slice(1);
const measurements = benchmarkLines
    .filter((line) => line.startsWith("measured build"))
    .map(
        (line) => line.split(',').slice(1).map(
            (value) => Number(value)
        )
    );

try {
    await summary.addHeading('Results')
        .addTable(
            benchmarkLines.map((line, index) => line.split(',').map(value => {
                if (index == 0) {
                    return {data: value, header: true};
                } else {
                    return value;
                }
            }))
        )
        .write()
} catch {
    // We don't care if it fails.
}

const iterations = measurements.length;

const scenarios = names.map((name, index) => {
    return {
        metric: `bitkey.app.devex.benchmark.duration`,
        points: measurements.map((values, valueIndex) => {
            return {
                // Introduce a simulated time series to gain more control in Datadog
                timestamp: roundedNow + (valueIndex * 60),
                value: values[index],
            };
        }),
        // The type of metric. The available types are 0 (unspecified), 1 (count), 2 (rate), and 3 (gauge). Allowed enum values: 0,1,2,3
        type: 3,
        unit: 'millisecond',
        tags: [
            `test:${testName}`,
            `scenario:${name}`,
            `iterations:${iterations}`,
            `branch:${branch}`,
            `commit:${commit}`,
            `env:${datadogEnvironment}`,
            ...tags,
        ]
    };
});

if (datadogApiKey) {
    const options = {
        method: 'POST',
        headers: {
            ['Content-Type']: 'application/json',
            "DD-API-KEY": datadogApiKey,
        },
    };
    const data = JSON.stringify({
        series: scenarios
    });
    console.log("Submitting measurements to Datadog");
    console.dir(data, { depth: null });

    const response = await asyncRequest('https://api.datadoghq.com/api/v2/series', options, data);
    console.log(`Data submitted, received response:`);
    console.dir(response, { depth: null });
} else {
    console.warn("Datadog API key not provided, upload skipped.");
    console.log("If specified, would upload the following measurements:");
    console.dir(scenarios, { depth: null });
}
