const fs = require('fs');
const path = require('path');

const targetDir = process.argv[2];
if (!targetDir) {
    console.error('Usage: node generate-sw.js <target-directory>');
    process.exit(1);
}

const distDir = path.resolve(__dirname, targetDir);
const templatePath = path.join(__dirname, 'src/jsMain/resources/sw.template.js');
const outputPath = path.join(distDir, 'sw.js');

// Critical file extensions / names that must be pre-cached for instant shell paint
const CRITICAL_PATTERNS = [
    '', // root (for navigation requests)
    'index.html',
    'manifest.json',
    'favicon.ico'
];
const CRITICAL_EXTENSIONS = ['.js', '.wasm', '.css'];
const CRITICAL_DIRS = ['icons'];

function isCritical(filePath) {
    if (CRITICAL_PATTERNS.includes(filePath)) return true;
    const ext = path.extname(filePath).toLowerCase();
    if (CRITICAL_EXTENSIONS.includes(ext)) return true;
    const dir = filePath.split('/')[0];
    if (CRITICAL_DIRS.includes(dir)) return true;
    return false;
}

// Recursively get all files, return as URL paths
function getAllFiles(dir, baseDir) {
    baseDir = baseDir || dir;
    let results = [];
    if (!fs.existsSync(dir)) return results;
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat && stat.isDirectory()) {
            results = results.concat(getAllFiles(fullPath, baseDir));
        } else {
            const relative = path.relative(baseDir, fullPath).replace(/\\/g, '/');
            results.push(relative);
        }
    });
    return results;
}

const files = getAllFiles(distDir);

// Ensure root and index.html are in the list
if (!files.includes('')) files.unshift('');
if (!files.includes('index.html')) files.unshift('index.html');

const criticalAssets = files.filter(f => isCritical(f));
const lazyAssets = files.filter(f => !isCritical(f));

console.log(`SW Generator: ${criticalAssets.length} critical, ${lazyAssets.length} lazy assets`);

const newContent = fs.readFileSync(templatePath, 'utf8')
    .replace('__CRITICAL_ASSETS_PLACEHOLDER__', JSON.stringify(criticalAssets, null, 2))
    .replace('__LAZY_ASSETS_PLACEHOLDER__', JSON.stringify(lazyAssets, null, 2))
    .replace('__CACHE_NAME__', Date.now());

fs.writeFileSync(outputPath, newContent);