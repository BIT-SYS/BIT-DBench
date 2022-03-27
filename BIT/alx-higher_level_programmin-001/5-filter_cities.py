#!/usr/bin/python3
""" script that takes in the name of a state as an argument and lists
    all cities of that state, using the database hbtn_0e_4_usa
"""

if __name__ == '__main__':
    # Standard Library imports
    import sys

    # related third party imports
    import MySQLdb as sql

    user = sys.argv[1]
    passwd = sys.argv[2]
    database = sys.argv[3]
    ui = sys.argv[4]  # ui = userinput

    conn = sql.connect(
            host='localhost',
            port=3306,
            user=user,
            passwd=passwd,
            db=database)

    cur = conn.cursor()

    cur.execute("SELECT c.name\
        FROM states s\
        JOIN cities c\
        ON s.id = c.state_id\
        WHERE s.name = '{}' ORDER BY c.id".format(ui))

    rows = cur.fetchall()

    print(", ".join([row[0] for row in rows]))

    cur.close()
    conn.close()
