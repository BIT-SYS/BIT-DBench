<?php
  // Check if both tutorcode and tutor name have been set.
  // If not, return browser to addtutor page with an error
  if($_POST['tutor']=="" or $_POST['tutorcode']=="") {
    header("Location: index.php?page=addtutor&error=blank");
  } else {

  // Set up variables storing both the tutor name and the tutorcode
  $tutor = $_POST['tutor'];
  $tutorcode = $_POST['tutorcode'];
  // Before adding record, check if it already exists
  // (Only do this if you can't have duplicate records)
  $check_sql = "SELECT * FROM tutorgroup WHERE tutorcode='$tutorcode'";
  $check_qry = mysqli_query($dbconnect, $check_sql);
  if(mysqli_num_rows($check_qry)>0) {
    header("Location: index.php?page=addtutor&error=exists");
  } else {
    // Run the insert query that adds the new tutor into the tutorgroup table
    $insert_sql = "INSERT INTO tutorgroup (tutor, tutorcode) VALUES ('$tutor', '$tutorcode')";
    $insert_qry = mysqli_query($dbconnect, $insert_sql);
  }


}
 ?>
 <!-- Display a success message -->
<div class="container-fluid">
  <div class="row">
    <div class="col">
      <p class="display-2">New tutor successfully added</p>
    </div>
  </div>
</div>
