require('dotenv').config();
const mariadb = require("mariadb");

let pool = mariadb.createPool({
  host: process.env.DB_HOST,
  user: process.env.DB_USER,
  password: process.env.DB_PASS,
  connectionLimit: 5
});

module.exports = {
  sendData: async function sendData(database, table, columns, data, replace=false) {
    if (Array.isArray(columns) && Array.isArray(data)) {
      columns = columns.join(", ");
      for (var i=0; i<data.length; ++i) {
        data[i] = `'${data[i]}'`;
      }
      data = data.join(", ");
    } else {
      data = `'${data}'`;
    }

    try {
      var conn = await pool.getConnection();
      if (replace == false) {
        return await conn.query(`INSERT INTO ${database}.${table} (${columns}) VALUES (${data})`);
      } else {
        return await conn.query(`REPLACE INTO ${database}.${table} (${columns}) VALUES (${data})`);
      }
    } finally {
      if (conn) conn.close();
    }
  },

  getData: async function getData(database, table) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SELECT * FROM ${database}.${table}`);
    } finally {
      if (conn) conn.close();
    }
  },

  getColumnData: async function getColumnData(database, table, column) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SELECT ${column} FROM ${database}.${table}`);
    } finally {
      if (conn) conn.close();
    }
  },

  getValueData: async function getValueData(database, table, column, value) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SELECT * FROM ${database}.${table} WHERE ${column}="${value}"`);
    } finally {
      if (conn) conn.close();
    }
  },

  getOrderedData: async function getOrderedData(database, table, column, order) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SELECT * FROM ${database}.${table} ORDER BY ${column} ${order}`);
    } finally {
      if (conn) conn.close();
    }
  },

  getOrderedLimitData: async function getOrderedLimitData(database, table, column, order, limit) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SELECT * FROM ${database}.${table} ORDER BY ${column} ${order} LIMIT ${limit}`);
    } finally {
      if (conn) conn.close();
    }
  },

  createTable: async function createTable(database, table, column, datatype) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`CREATE TABLE IF NOT EXISTS ${database}.${table} (${column} ${datatype})`);
    } finally {
      if (conn) conn.close();
    }
  },

  showTables: async function showTables(database) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`SHOW TABLES FROM ${database}`);
    } finally {
      if (conn) conn.close();
    }
  },


  dropValueData: async function dropTable(database, table, column, value) {
    try {
      var conn = await pool.getConnection();
      return await conn.query(`DELETE FROM ${database}.${table} WHERE ${column}='${value}'`);
    } finally {
      if (conn) conn.close();
    }
  }
}