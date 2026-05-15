import fs from 'fs';
const stats = fs.statSync('test_card.png');
console.log('Size:', stats.size);
