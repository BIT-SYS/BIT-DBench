<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

require_once("rest/dao/TodoDao.class.php");

$id = $_REQUEST['id'];

$dao = new TodoDao();
$dao->delete($id);

echo "DELETED $id";
?>
